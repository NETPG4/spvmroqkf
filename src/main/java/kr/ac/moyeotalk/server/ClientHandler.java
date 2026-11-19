package kr.ac.moyeotalk.server;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import kr.ac.moyeotalk.common.*;
import kr.ac.moyeotalk.server.user.*;

/** 한 연결의 수신·전송·종료를 관리하고 명령어를 담당 클래스에 전달한다. */
public final class ClientHandler implements Runnable, AutoCloseable {
    // 연결마다 대기 메시지 수를 제한하여 느린 수신자가 메모리를 계속 점유하지 못하게 한다.
    static final int MAX_PENDING_MESSAGES = 128;
    private final BlockingQueue<String> outgoing = new ArrayBlockingQueue<>(MAX_PENDING_MESSAGES);
    private final Socket socket;
    private final ChatServer server;
    private final BufferedReader reader;
    private final BufferedWriter writer;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final long connectedAt = System.nanoTime();
    private volatile User user;
    // 입력 종료와 응답 전송 완료를 구분한다. 로그아웃 응답은 보낸 후 연결을 닫는다.
    private volatile boolean draining;
    private volatile boolean aborted;
    private volatile long drainingAt;
    private volatile long writingAt;

    ClientHandler(Socket socket, ChatServer server) throws IOException {
        this.socket = socket;
        this.server = server;
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    // 종료 응답을 보내는 중에도 새로운 로그인이나 전송은 받지 않는다.
    public boolean isClosed() { return closed.get() || aborted || draining; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    /** 수신 담당 스레드. 잘못된 요청은 오류를 보내고 다음 요청을 계속 받는다. */
    @Override public void run() {
        try {
            send("/WELCOME MOYEOTALK");
            String line;
            while (!isClosed() && (line = WireCodec.readLine(reader)) != null) {
                if (isClosed()) break;
                try { dispatch(Message.parse(line)); }
                catch (IllegalArgumentException e) { error("BAD_REQUEST", e.getMessage()); }
            }
        } catch (IOException e) {
            if (!isClosed()) System.err.println("클라이언트 수신 종료: " + e.getClass().getSimpleName());
        } catch (RuntimeException e) {
            System.err.println("명령어 처리 실패: " + e);
        } finally {
            // 정상 로그아웃의 마지막 응답은 전송 담당 스레드가 보내고 정리한다.
            if (!draining) close();
        }
    }

    // 로그인 상태를 검사한 뒤 명령어를 사용자·방·게임 기능으로 전달한다.
    private void dispatch(Message message) {
        // 로그인 전에는 로그인과 연결 종료 명령어만 허용한다.
        if (user == null && !message.command().equals("LOGIN") && !message.command().equals("QUIT")) {
            error("LOGIN_REQUIRED", "먼저 로그인하세요"); return;
        }
        // 명령어 이름에 따라 실행할 기능을 선택한다. this는 현재 연결 객체다.
        switch (message.command()) {
            // 사용자 등록과 로그인 중복 검사를 맡긴다.
            case "LOGIN" -> server.users.login(this, message);
            // 현재 로그인한 사용자 목록을 요청한 연결에 보낸다.
            case "USER_LIST" -> server.users.listUsers(this, message);
            // 로그아웃과 종료 명령어는 같은 연결 종료 처리를 사용한다.
            case "LOGOUT", "QUIT" -> server.users.logout(this, message);
            // 방 생성 요청을 방 관리자에게 전달한다.
            case "CREATE_ROOM" -> server.rooms.createRoom(this, message);
            // 기존 방 참여 요청을 전달한다.
            case "JOIN_ROOM" -> server.rooms.joinRoom(this, message);
            // 참여 중인 방에서 나가는 요청을 전달한다.
            case "LEAVE_ROOM" -> server.rooms.leaveRoom(this, message);
            // 방 삭제 요청을 전달한다.
            case "DELETE_ROOM" -> server.rooms.deleteRoom(this, message);
            // 채팅 메시지 처리와 발신자 확인을 맡긴다.
            case "CHAT" -> server.rooms.chat(this, message);
            // 기존 메시지 삭제 요청을 전달한다.
            case "DELETE_MESSAGE" -> server.rooms.deleteMessage(this, message);
            // 메시지 읽음 처리 요청을 전달한다.
            case "READ_MESSAGE" -> server.rooms.readMessage(this, message);
            // 관련된 세 명령어를 게임 담당 클래스의 공통 진입점에 전달한다.
            case "GAME_RECORD", "COIN_UPDATE", "EMOTICON_SEND" -> server.games.handle(this, message);
            // 등록되지 않은 명령어에는 알 수 없는 명령어 오류를 보낸다.
            default -> error("UNKNOWN_COMMAND", "알 수 없는 명령어입니다");
        }
    }
    /** 큐에만 넣고 즉시 반환한다. 사용자 관리 잠금 안에서도 네트워크를 기다리지 않는다. */
    public synchronized void send(String line) {
        WireCodec.validate(line);
        if (isClosed()) return;
        if (!outgoing.offer(line)) abortTransport();
    }

    /** 로그아웃 응답을 마지막에 넣고, 이전 응답까지 순서대로 전송한 뒤 닫는다. */
    public synchronized void sendAndClose(String line) {
        send(line);
        if (isClosed()) return;
        drainingAt = System.nanoTime();
        draining = true;
    }

    /** 한 연결의 출력은 이 작업 하나만 담당하므로 응답 순서가 뒤섞이지 않는다. */
    void writeMessages() {
        try {
            while (!closed.get() && !aborted) {
                String line = outgoing.poll(100, TimeUnit.MILLISECONDS);
                if (line != null) {
                    writingAt = System.nanoTime();
                    try {
                        writer.write(line);
                        // 운영체제와 관계없이 LF를 사용하여 최대 길이 메시지도 동일하게 전송한다.
                        writer.write('\n');
                        writer.flush();
                    } finally { writingAt = 0; }
                }
                if (draining && outgoing.isEmpty()) break;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            if (!isClosed()) System.err.println("클라이언트 전송 종료: " + e.getClass().getSimpleName());
        } finally { close(); }
    }

    /** 서버의 주기적 검사에서 호출한다. 조금씩 입력하더라도 로그인 마감은 연장하지 않는다. */
    void checkTimeouts(long now) {
        if (closed.get() || aborted) return;
        long started = writingAt;
        if ((user == null && now - connectedAt >= server.loginTimeoutNanos)
                || (started != 0 && now - started >= server.writeTimeoutNanos)
                || (draining && now - drainingAt >= server.writeTimeoutNanos)) {
            abortTransport();
        }
    }

    // 여기서는 소켓만 끊는다. 사용자 목록 순회 중 재진입하여 목록을 변경하지 않는다.
    private void abortTransport() {
        aborted = true;
        try { socket.close(); }
        catch (IOException e) { System.err.println("소켓 종료 실패: " + e); }
    }

    public void error(String code, String detail) { send("/ERROR " + code + " " + detail); }

    /** 여러 스레드가 종료를 요청해도 정리는 한 번만 실행하고, 단계별 실패를 격리한다. */
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        abortTransport();
        outgoing.clear();
        try { server.rooms.disconnect(this); }
        catch (RuntimeException e) { System.err.println("방 연결 정리 실패: " + e); }
        finally {
            try { server.users.disconnect(this); }
            catch (RuntimeException e) { System.err.println("사용자 정리 실패: " + e); }
            finally { server.remove(this); }
        }
    }
}

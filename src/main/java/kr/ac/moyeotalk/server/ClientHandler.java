package kr.ac.moyeotalk.server;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import kr.ac.moyeotalk.common.*;
import kr.ac.moyeotalk.server.user.*;

/** 연결마다 하나씩 생성. switch에는 담당 영역 메서드 호출만 둔다. */
public final class ClientHandler implements Runnable, AutoCloseable {
    private final Socket socket;
    private final ChatServer server;
    private final BufferedReader reader;
    private final BufferedWriter writer;
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile User user;

    ClientHandler(Socket socket, ChatServer server) throws IOException {
        this.socket = socket;
        this.server = server;
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }
    public boolean isClosed() { return closed.get(); }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    @Override public void run() {
        try {
            send("/WELCOME MOYEOTALK");
            String line;
            while (!closed.get() && (line = WireCodec.readLine(reader)) != null) {
                try { dispatch(Message.parse(line)); }
                catch (IllegalArgumentException e) { error("BAD_REQUEST", e.getMessage()); }
            }
        } catch (IOException e) {
            // EOF, 강제 종료, 통신 실패 모두 finally에서 같은 정리 경로를 사용한다.
        } finally { close(); }
    }
    private void dispatch(Message message) {
        if (user == null && !message.command().equals("LOGIN") && !message.command().equals("QUIT")) {
            error("LOGIN_REQUIRED", "먼저 로그인하세요"); return;
        }
        switch (message.command()) {
            case "LOGIN" -> server.users.login(this, message);
            case "USER_LIST" -> server.users.listUsers(this, message);
            case "LOGOUT", "QUIT" -> server.users.logout(this, message);
            case "CREATE_ROOM" -> server.rooms.createRoom(this, message);
            case "JOIN_ROOM" -> server.rooms.joinRoom(this, message);
            case "LEAVE_ROOM" -> server.rooms.leaveRoom(this, message);
            case "DELETE_ROOM" -> server.rooms.deleteRoom(this, message);
            case "CHAT" -> server.rooms.chat(this, message);
            case "DELETE_MESSAGE" -> server.rooms.deleteMessage(this, message);
            case "READ_MESSAGE" -> server.rooms.readMessage(this, message);
            case "GAME_RECORD", "COIN_UPDATE", "EMOTICON_SEND" -> server.games.handle(this, message);
            default -> error("UNKNOWN_COMMAND", "알 수 없는 명령어입니다");
        }
    }
    public synchronized void send(String line) {
        WireCodec.validate(line);
        if (closed.get()) return;
        try { writer.write(line); writer.newLine(); writer.flush(); }
        catch (IOException e) {
            // 잠금 안에서 사용자 목록을 수정하지 않는다. 소켓을 닫아 읽기 스레드가 정리하게 한다.
            try { socket.close(); } catch (IOException ignored) {}
        }
    }
    public void error(String code, String detail) { send("/ERROR " + code + " " + detail); }
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        try { socket.close(); } catch (IOException ignored) {}
        try { server.rooms.disconnect(this); }
        finally { server.users.disconnect(this); server.remove(this); }
    }
}

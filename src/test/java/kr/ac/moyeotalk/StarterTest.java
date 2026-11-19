package kr.ac.moyeotalk;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import kr.ac.moyeotalk.common.*;
import kr.ac.moyeotalk.server.*;
import kr.ac.moyeotalk.server.chat.RoomManager;
import kr.ac.moyeotalk.server.game.GameService;

/** 외부 라이브러리 없이 실행하는 실제 TCP 통합 테스트. */
public final class StarterTest {
    /** 테스트에서 사용자 한 명처럼 접속하고 서버 응답을 확인하는 도우미. */
    static final class Peer implements AutoCloseable {
        // 테스트 사용자와 서버 사이의 연결.
        final Socket socket;
        // 서버 응답을 한 줄씩 읽는 입력 통로.
        final BufferedReader in;
        // 테스트 명령어를 서버로 보내는 출력 통로.
        final PrintWriter out;
        // 같은 컴퓨터에서 실행 중인 테스트 서버에 접속한다.
        Peer(int port) throws IOException {
            socket = new Socket("127.0.0.1", port);
            // 응답이 없을 때 테스트가 멈추지 않도록 읽기 대기를 3초로 제한한다.
            socket.setSoTimeout(3000);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            // 마지막 인자 true는 println() 호출 시 내용을 즉시 전송하게 한다.
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            // 접속 직후 환영 응답을 받는지 확인한다.
            expect("/WELCOME MOYEOTALK");
        }
        // 명령어 끝에 줄바꿈을 붙여 서버에 보낸다.
        void send(String line) { out.println(line); }
        // 실제 응답이 예상 문자열과 다르면 테스트를 실패시킨다.
        void expect(String expected) throws IOException {
            String actual = in.readLine();
            if (!expected.equals(actual)) throw new AssertionError("expected=" + expected + ", actual=" + actual);
        }
        // 오류 응답인지 확인하고, 예상한 오류 코드가 포함되어 있는지도 검사한다.
        void error(String code) throws IOException {
            String line = in.readLine();
            if (line == null || !line.startsWith("/ERROR " + code + " ")) throw new AssertionError(line);
        }
        // 테스트 연결을 닫는다. 자원 자동 정리 구문에서도 호출된다.
        public void close() throws IOException { socket.close(); }
    }
    // 문자열 처리 검사 후 실제 서버 접속 검사를 순서대로 실행한다.
    public static void main(String[] args) throws Exception {
        // 소켓 없이 문자열 분리, 공백 보존, 잘못된 입력 처리를 검사한다.
        String body = Message.parse("/CHAT room1 user1 안녕  하세요").fields(3)[2];
        // 채팅 본문의 연속된 두 공백이 그대로 남아 있어야 한다.
        if (!body.equals("안녕  하세요")) throw new AssertionError(body);
        // 윈도우 방식의 줄바꿈은 제거되고 명령어만 남아야 한다.
        if (!WireCodec.readLine(new StringReader("/USER_LIST\r\n")).equals("/USER_LIST")) throw new AssertionError();
        // 줄바꿈 없이 끝난 메시지는 완성된 명령어로 처리하면 안 된다.
        if (WireCodec.readLine(new StringReader("/QUIT")) != null) throw new AssertionError();
        // 잘못된 입력에서 예외가 발생해야 성공이며, 발생하지 않으면 실패다.
        try { WireCodec.readLine(new StringReader("x".repeat(8193))); throw new AssertionError(); }
        catch (IOException expected) {}
        // 시작 기호 /가 없는 입력을 거부하는지 확인한다.
        try { Message.parse("CHAT room1 user1 text"); throw new AssertionError(); }
        catch (IllegalArgumentException expected) {}
        // 필요한 인자가 부족하면 예외가 발생해야 한다.
        try { Message.parse("/CHAT room1").fields(3); throw new AssertionError(); }
        catch (IllegalArgumentException expected) {}
        // 본문 안에 줄바꿈을 넣어 추가 명령어를 보내는 입력을 거부해야 한다.
        try { WireCodec.validate("/CHAT room1 user1 hi\n/QUIT"); throw new AssertionError(); }
        catch (IllegalArgumentException expected) {}
        // codec 인자를 주면 소켓을 사용하는 검사 전에 종료한다.
        if (args.length > 0 && args[0].equals("codec")) {
            System.out.println("PASS: 문자열 명령어, 공백 보존, CRLF, 불완전한 프레임, 길이 제한, 잘못된 인자, 개행 주입 검사");
            return;
        }
        // 방 생성 기능 대신 호출 여부와 전달된 인자만 확인하는 테스트 객체를 쓴다.
        // 서버 스레드가 받은 인자를 테스트 스레드로 안전하게 전달하는 대기열.
        BlockingQueue<String> delegated = new LinkedBlockingQueue<>();
        // 이 객체에서만 방 생성 메서드를 바꾸는 익명 클래스를 만든다.
        RoomManager rooms = new RoomManager() {
            // 실제 방 생성 대신 인자를 기록하고 테스트 확인용 응답을 보낸다.
            @Override public void createRoom(ClientHandler c, Message m) {
                delegated.add(m.arguments());
                c.send("/TEST_ROOM_DISPATCH_OK");
            }
        };
        // 서버 실행을 담당할 작업 스레드 하나를 준비한다.
        ExecutorService runner = Executors.newSingleThreadExecutor();
        // 포트 0은 운영체제가 빈 포트를 고르게 한다. try 블록이 끝나면 자동으로 닫힌다.
        try (ChatServer server = new ChatServer(0, rooms, new GameService())) {
            // 서버를 별도 스레드에서 실행하여 아래 테스트 접속을 동시에 진행한다.
            Future<?> serving = runner.submit(() -> { try { server.start(); } catch (IOException e) { throw new UncheckedIOException(e); } });
            try (Peer a = new Peer(server.getPort()); Peer b = new Peer(server.getPort())) {
                // 로그인 전 목록 요청은 거부되어야 한다.
                a.send("/USER_LIST"); a.error("LOGIN_REQUIRED");
                // 첫 사용자 로그인 후 성공 응답과 한 명짜리 목록을 확인한다.
                a.send("/LOGIN user1 테스터1"); a.expect("/LOGIN_OK user1 테스터1"); a.expect("/USER_LIST 1 user1 테스터1");
                // 두 번째 연결이 같은 ID를 쓰면 중복 오류가 나야 한다.
                b.send("/LOGIN user1 중복"); b.error("DUPLICATE_USER");
                // 다른 ID를 쓰면 두 번째 연결도 로그인할 수 있어야 한다.
                b.send("/LOGIN user2 테스터2"); b.expect("/LOGIN_OK user2 테스터2");
                // 두 연결 모두 같은 두 명짜리 사용자 목록을 받아야 한다.
                a.expect("/USER_LIST 2 user1 테스터1 user2 테스터2"); b.expect("/USER_LIST 2 user1 테스터1 user2 테스터2");
                // 재로그인, 잘못된 명령어, 발신자 사칭이 거부되는지 확인한다.
                a.send("/LOGIN user3 변경"); a.error("ALREADY_LOGGED_IN");
                // 로그인 인자가 빠진 요청은 잘못된 요청으로 처리해야 한다.
                a.send("/LOGIN"); a.error("BAD_REQUEST");
                // 인자가 필요 없는 명령어에 값을 붙이면 거부해야 한다.
                a.send("/USER_LIST extra"); a.error("BAD_REQUEST");
                // 지원하지 않는 명령어는 별도의 오류 코드로 구분해야 한다.
                a.send("/BOGUS"); a.error("UNKNOWN_COMMAND");
                // 시작 기호가 빠진 명령어도 서버에서 거부해야 한다.
                a.send("CHAT room1 user1 test"); a.error("BAD_REQUEST");
                // 다른 로그인 사용자의 ID로 채팅을 보내면 거부해야 한다.
                a.send("/CHAT room1 user2 사칭"); a.error("SENDER_MISMATCH");
                // 발신자가 일치하면 다음 단계인 미구현 기능 응답까지 도달해야 한다.
                a.send("/CHAT room1 user1 안녕 하세요"); a.error("NOT_IMPLEMENTED");
                // 방 생성 요청이 테스트용 방 관리자에 전달되는지 확인한다.
                a.send("/CREATE_ROOM room1 우리 방"); a.expect("/TEST_ROOM_DISPATCH_OK");
                // 최대 3초 기다려 전달된 인자의 내용과 공백이 유지되었는지 확인한다.
                if (!"room1 우리 방".equals(delegated.poll(3, TimeUnit.SECONDS))) throw new AssertionError();
                // 로그아웃 뒤 같은 ID로 재접속할 수 있는지 확인한다.
                b.send("/LOGOUT"); b.expect("/LOGOUT_OK");
                // 로그아웃 응답 뒤에는 서버가 연결을 닫아 입력 종료가 확인되어야 한다.
                if (b.in.readLine() != null) throw new AssertionError("로그아웃 후 연결이 남아 있음");
                a.expect("/USER_LIST 1 user1 테스터1");
                // 로그아웃한 ID를 새 연결에서 다시 사용할 수 있어야 한다.
                try (Peer reconnect = new Peer(server.getPort())) {
                    reconnect.send("/LOGIN user2 테스터2"); reconnect.expect("/LOGIN_OK user2 테스터2");
                    // 재접속한 사용자와 기존 사용자 모두 갱신된 목록을 받아야 한다.
                    reconnect.expect("/USER_LIST 2 user1 테스터1 user2 테스터2"); a.expect("/USER_LIST 2 user1 테스터1 user2 테스터2");
                }
                a.expect("/USER_LIST 1 user1 테스터1"); // 비정상 종료 정리
                // 자동 목록 알림 외에 직접 조회한 결과도 정확한지 검사한다.
                a.send("/USER_LIST"); a.expect("/USER_LIST 1 user1 테스터1");
                // 서버를 종료하여 남은 클라이언트 연결도 함께 끊기는지 확인한다.
                server.close();
                // 서버 종료 후에는 남아 있던 연결에서도 입력 종료가 확인되어야 한다.
                if (a.in.readLine() != null) throw new AssertionError("서버 종료 후 연결이 남아 있음");
            }
            // 서버 실행 작업도 3초 안에 종료되어야 테스트를 통과한다.
            serving.get(3, TimeUnit.SECONDS);
        // 성공 여부와 관계없이 테스트용 작업 스레드를 정리한다.
        } finally { runner.shutdownNow(); }
        System.out.println("PASS: 문자열 파싱, 로그인, 중복 접속, 사용자 목록, 검증, B 연결, 로그아웃, 재접속, 비정상 종료, 서버 종료");
    }
}

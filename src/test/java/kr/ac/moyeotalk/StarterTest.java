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
    static final class Peer implements AutoCloseable {
        final Socket socket;
        final BufferedReader in;
        final PrintWriter out;
        Peer(int port) throws IOException {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(3000);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            expect("/WELCOME MOYEOTALK");
        }
        void send(String line) { out.println(line); }
        void expect(String expected) throws IOException {
            String actual = in.readLine();
            if (!expected.equals(actual)) throw new AssertionError("expected=" + expected + ", actual=" + actual);
        }
        void error(String code) throws IOException {
            String line = in.readLine();
            if (line == null || !line.startsWith("/ERROR " + code + " ")) throw new AssertionError(line);
        }
        public void close() throws IOException { socket.close(); }
    }
    public static void main(String[] args) throws Exception {
        String body = Message.parse("/CHAT room1 user1 안녕  하세요").fields(3)[2];
        if (!body.equals("안녕  하세요")) throw new AssertionError(body);
        if (!WireCodec.readLine(new StringReader("/USER_LIST\r\n")).equals("/USER_LIST")) throw new AssertionError();
        if (WireCodec.readLine(new StringReader("/QUIT")) != null) throw new AssertionError();
        try { WireCodec.readLine(new StringReader("x".repeat(8193))); throw new AssertionError(); }
        catch (IOException expected) {}
        try { Message.parse("CHAT room1 user1 text"); throw new AssertionError(); }
        catch (IllegalArgumentException expected) {}
        try { Message.parse("/CHAT room1").fields(3); throw new AssertionError(); }
        catch (IllegalArgumentException expected) {}
        try { WireCodec.validate("/CHAT room1 user1 hi\n/QUIT"); throw new AssertionError(); }
        catch (IllegalArgumentException expected) {}
        if (args.length > 0 && args[0].equals("codec")) {
            System.out.println("PASS: 문자열 명령어, 공백 보존, CRLF, 불완전한 프레임, 길이 제한, 잘못된 인자, 개행 주입 검사");
            return;
        }
        BlockingQueue<String> delegated = new LinkedBlockingQueue<>();
        RoomManager rooms = new RoomManager() {
            @Override public void createRoom(ClientHandler c, Message m) {
                delegated.add(m.arguments());
                c.send("/TEST_ROOM_DISPATCH_OK");
            }
        };
        ExecutorService runner = Executors.newSingleThreadExecutor();
        try (ChatServer server = new ChatServer(0, rooms, new GameService())) {
            Future<?> serving = runner.submit(() -> { try { server.start(); } catch (IOException e) { throw new UncheckedIOException(e); } });
            try (Peer a = new Peer(server.getPort()); Peer b = new Peer(server.getPort())) {
                a.send("/USER_LIST"); a.error("LOGIN_REQUIRED");
                a.send("/LOGIN user1 정환"); a.expect("/LOGIN_OK user1 정환"); a.expect("/USER_LIST 1 user1 정환");
                b.send("/LOGIN user1 중복"); b.error("DUPLICATE_USER");
                b.send("/LOGIN user2 은서"); b.expect("/LOGIN_OK user2 은서");
                a.expect("/USER_LIST 2 user1 정환 user2 은서"); b.expect("/USER_LIST 2 user1 정환 user2 은서");
                a.send("/LOGIN user3 변경"); a.error("ALREADY_LOGGED_IN");
                a.send("/LOGIN"); a.error("BAD_REQUEST");
                a.send("/USER_LIST extra"); a.error("BAD_REQUEST");
                a.send("/BOGUS"); a.error("UNKNOWN_COMMAND");
                a.send("CHAT room1 user1 test"); a.error("BAD_REQUEST");
                a.send("/CHAT room1 user2 사칭"); a.error("SENDER_MISMATCH");
                a.send("/CHAT room1 user1 안녕 하세요"); a.error("NOT_IMPLEMENTED");
                a.send("/CREATE_ROOM room1 우리 방"); a.expect("/TEST_ROOM_DISPATCH_OK");
                if (!"room1 우리 방".equals(delegated.poll(3, TimeUnit.SECONDS))) throw new AssertionError();
                b.send("/LOGOUT"); b.expect("/LOGOUT_OK");
                if (b.in.readLine() != null) throw new AssertionError("로그아웃 후 연결이 남아 있음");
                a.expect("/USER_LIST 1 user1 정환");
                try (Peer reconnect = new Peer(server.getPort())) {
                    reconnect.send("/LOGIN user2 은서"); reconnect.expect("/LOGIN_OK user2 은서");
                    reconnect.expect("/USER_LIST 2 user1 정환 user2 은서"); a.expect("/USER_LIST 2 user1 정환 user2 은서");
                }
                a.expect("/USER_LIST 1 user1 정환"); // 비정상 종료 정리
                a.send("/USER_LIST"); a.expect("/USER_LIST 1 user1 정환");
                server.close();
                if (a.in.readLine() != null) throw new AssertionError("서버 종료 후 연결이 남아 있음");
            }
            serving.get(3, TimeUnit.SECONDS);
        } finally { runner.shutdownNow(); }
        System.out.println("PASS: 문자열 파싱, 로그인, 중복 접속, 사용자 목록, 검증, B 연결, 로그아웃, 재접속, 비정상 종료, 서버 종료");
    }
}

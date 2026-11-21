package kr.ac.moyeotalk;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import kr.ac.moyeotalk.server.ChatServer;

/** 실제 TCP로 채팅방, 메시지 번호, 읽음 처리, 연결 끊김 처리를 확인하는 테스트. */
public final class RoomTest {
    // 테스트용 사용자 한 명. 서버에 접속해서 보내고 받는다.
    static final class Peer implements AutoCloseable {
        final Socket socket;
        final BufferedReader in;
        final PrintWriter out;

        Peer(int port) throws IOException {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(3000); // 응답이 안 오면 3초 뒤 실패
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
            if (line == null || !line.startsWith("/ERROR " + code + " ")) throw new AssertionError(code + " 기대, 실제=" + line);
        }

        // 로그인 성공 응답과 그때 오는 접속자 목록 알림을 받아 둔다.
        void login(String id, String nick, String firstList) throws IOException {
            send("/LOGIN " + id + " " + nick);
            expect("/LOGIN_OK " + id + " " + nick);
            expect(firstList);
        }

        @Override public void close() throws IOException { socket.close(); }
    }

    public static void main(String[] args) throws Exception {
        ExecutorService runner = Executors.newSingleThreadExecutor();
        try (ChatServer server = new ChatServer(0)) {
            Future<?> serving = runner.submit(() -> { try { server.start(); } catch (IOException e) { throw new UncheckedIOException(e); } });
            try (Peer a = new Peer(server.getPort()); Peer b = new Peer(server.getPort()); Peer c = new Peer(server.getPort())) {
                // 로그인: 새 사용자가 들어올 때마다 기존 사용자도 목록 알림을 받는다.
                a.login("user1", "테스터1", "/USER_LIST 1 user1 테스터1");
                b.login("user2", "테스터2", "/USER_LIST 2 user1 테스터1 user2 테스터2");
                a.expect("/USER_LIST 2 user1 테스터1 user2 테스터2");
                c.login("user3", "테스터3", "/USER_LIST 3 user1 테스터1 user2 테스터2 user3 테스터3");
                a.expect("/USER_LIST 3 user1 테스터1 user2 테스터2 user3 테스터3");
                b.expect("/USER_LIST 3 user1 테스터1 user2 테스터2 user3 테스터3");

                // 방 만들기: 방 이름의 공백은 유지되고, 중복 ID와 잘못된 요청은 거부한다.
                a.send("/CREATE_ROOM room1 우리  방"); a.expect("/ROOM_CREATED room1 우리  방");
                a.send("/CREATE_ROOM room1 또 방"); a.error("DUPLICATE_ROOM");
                a.send("/CREATE_ROOM bad!id 이름"); a.error("BAD_REQUEST");
                a.send("/CREATE_ROOM room2"); a.error("BAD_REQUEST");

                // 방에 들어가지 않은 사람은 채팅과 읽음 처리를 할 수 없다.
                c.send("/JOIN_ROOM nothing"); c.error("ROOM_NOT_FOUND");
                c.send("/CHAT room1 user3 몰래"); c.error("NOT_IN_ROOM");
                c.send("/READ_MESSAGE room1 1"); c.error("NOT_IN_ROOM");
                c.send("/CHAT room1 user1 사칭"); c.error("SENDER_MISMATCH");

                // 입장: 중복 입장은 거부하고, 성공하면 기존 사람에게만 알린다.
                b.send("/JOIN_ROOM room1"); b.expect("/JOIN_OK room1 우리  방");
                a.expect("/ROOM_JOINED room1 user2 테스터2");
                b.send("/JOIN_ROOM room1"); b.error("ALREADY_IN_ROOM");
                c.send("/JOIN_ROOM room1"); c.expect("/JOIN_OK room1 우리  방");
                a.expect("/ROOM_JOINED room1 user3 테스터3");
                b.expect("/ROOM_JOINED room1 user3 테스터3");

                // 채팅: 보낸 사람을 포함해 모두 받고, 메시지 번호가 1부터 붙으며 본문 공백이 유지된다.
                a.send("/CHAT room1 user1 안녕  하세요");
                for (Peer p : new Peer[] {a, b, c}) p.expect("/CHAT room1 1 user1 안녕  하세요");
                b.send("/CHAT room1 user2 반가워요");
                for (Peer p : new Peer[] {a, b, c}) p.expect("/CHAT room1 2 user2 반가워요");

                // 읽음: 읽을 때마다 방 전체가 안 읽은 사람 수를 받는다. (1번 메시지는 보낸 사람 a가 이미 읽은 상태)
                b.send("/READ_MESSAGE room1 1");
                for (Peer p : new Peer[] {a, b, c}) p.expect("/MESSAGE_READ room1 1 user2 1");
                c.send("/READ_MESSAGE room1 1");
                for (Peer p : new Peer[] {a, b, c}) p.expect("/MESSAGE_READ room1 1 user3 0");

                // 이미 읽은 메시지를 다시 읽거나 자기 메시지를 읽으면 아무 알림도 없다.
                // 바로 다음에 보낸 요청의 응답이 첫 줄로 오면 중간에 알림이 없었다는 뜻이다.
                b.send("/READ_MESSAGE room1 1"); b.send("/READ_MESSAGE room1 99"); b.error("MESSAGE_NOT_FOUND");
                b.send("/READ_MESSAGE room1 abc"); b.error("BAD_REQUEST");
                a.send("/READ_MESSAGE room1 1");
                a.send("/READ_MESSAGE room1 2");
                for (Peer p : new Peer[] {a, b, c}) p.expect("/MESSAGE_READ room1 2 user1 1");

                // 퇴장: 나간 사람은 방 알림과 채팅을 받지 않고 다시 쓸 수도 없다.
                c.send("/LEAVE_ROOM room1"); c.expect("/LEAVE_OK room1");
                a.expect("/ROOM_LEFT room1 user3"); b.expect("/ROOM_LEFT room1 user3");
                c.send("/LEAVE_ROOM room1"); c.error("NOT_IN_ROOM");
                c.send("/CHAT room1 user3 나감"); c.error("NOT_IN_ROOM");

                // 아직 구현하지 않은 기능은 성공을 가장하지 않는다.
                a.send("/DELETE_ROOM room1"); a.error("NOT_IMPLEMENTED");
                a.send("/DELETE_MESSAGE room1 1"); a.error("NOT_IMPLEMENTED");

                // 보관 한도: 방마다 최근 200개만 보관하므로 201개를 보내면 1번 메시지는 지워진다.
                a.send("/CREATE_ROOM room2 혼자방"); a.expect("/ROOM_CREATED room2 혼자방");
                for (int i = 1; i <= 201; i++) {
                    a.send("/CHAT room2 user1 m" + i);
                    a.expect("/CHAT room2 " + i + " user1 m" + i);
                }
                a.send("/READ_MESSAGE room2 1"); a.error("MESSAGE_NOT_FOUND");

                // 연결이 갑자기 끊기면 방에서 빠지고 남은 사람에게 알린다. 방 알림이 목록 알림보다 먼저 온다.
                b.close();
                a.expect("/ROOM_LEFT room1 user2");
                a.expect("/USER_LIST 2 user1 테스터1 user3 테스터3");
                c.expect("/USER_LIST 2 user1 테스터1 user3 테스터3");

                server.close();
            }
            serving.get(3, TimeUnit.SECONDS);
        } finally { runner.shutdownNow(); }
        System.out.println("PASS: 방 생성·입장·퇴장, 메시지 번호, 읽음 처리, 방 밖 사용자 차단, 보관 한도, 연결 끊김 정리");
    }
}

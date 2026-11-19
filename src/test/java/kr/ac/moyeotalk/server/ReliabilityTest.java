package kr.ac.moyeotalk.server;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import kr.ac.moyeotalk.server.chat.RoomManager;
import kr.ac.moyeotalk.server.game.GameService;

/** 포트 없이 실제 서버 처리 코드를 실행한다. 운영체제 TCP 검증을 대신하지는 않는다. */
public final class ReliabilityTest {
    public static void main(String[] args) throws Exception {
        lifecycle();
        concurrentLogin();
        loginDeadline();
        connectionLimit();
        oversizedFrame();
        slowReceiver();
        queueLimit();
        cleanupFailure();
        System.out.println("PASS: 소켓 대역 기반 로그인·목록·재접속·동시성·제한시간·종료예외·스레드 정리");
    }

    private static void lifecycle() throws Exception {
        try (Fixture f = new Fixture(new RoomManager(), 5000, 1000)) {
            try (Peer a = f.connect(); Peer b = f.connect()) {
                a.send("/USER_LIST"); a.error("LOGIN_REQUIRED");
                a.send("/LOGIN user1 테스터1"); a.expect("/LOGIN_OK user1 테스터1"); a.expect(list1());
                b.send("/LOGIN user1 중복"); b.error("DUPLICATE_USER");
                b.send("/LOGIN user2 테스터2"); b.expect("/LOGIN_OK user2 테스터2");
                a.expect(list2()); b.expect(list2());
                a.send("/LOGIN"); a.error("BAD_REQUEST");
                a.send("/LOGIN user3 변경"); a.error("ALREADY_LOGGED_IN");
                a.send("/BOGUS"); a.error("UNKNOWN_COMMAND");
                a.send("/CHAT room1 user2 본문"); a.error("SENDER_MISMATCH");
                b.send("/USER_LIST"); b.send("/LOGOUT");
                // 앞선 응답과 로그아웃 응답을 순서대로 보낸 뒤 연결을 닫아야 한다.
                b.expect(list2()); b.expect("/LOGOUT_OK"); b.eof(); a.expect(list1());
                try (Peer reconnect = f.connect()) {
                    reconnect.send("/LOGIN user2 테스터2"); reconnect.expect("/LOGIN_OK user2 테스터2");
                    reconnect.expect(list2()); a.expect(list2());
                }
                a.expect(list1());
                a.send("/USER_LIST"); a.expect(list1());
                f.server.close(); a.eof();
            }
        }
    }

    private static void concurrentLogin() throws Exception {
        try (Fixture f = new Fixture(new RoomManager(), 5000, 1000)) {
            List<Peer> peers = new ArrayList<>();
            ExecutorService tasks = Executors.newFixedThreadPool(8);
            try {
                for (int i = 0; i < 8; i++) peers.add(f.connect());
                CountDownLatch start = new CountDownLatch(1);
                List<Future<?>> sent = new ArrayList<>();
                for (Peer p : peers) sent.add(tasks.submit(() -> {
                    start.await(); p.send("/LOGIN same 테스트"); return null;
                }));
                start.countDown();
                for (Future<?> result : sent) result.get(3, TimeUnit.SECONDS);
                int winners = 0;
                for (Peer p : peers) {
                    String result = p.read();
                    if (result.equals("/LOGIN_OK same 테스트")) {
                        winners++; p.expect("/USER_LIST 1 same 테스트");
                    } else check(result.startsWith("/ERROR DUPLICATE_USER "), result);
                }
                check(winners == 1, "동일 ID는 한 연결만 등록되어야 함");
                // 연결을 동시에 끊어도 사용자 목록이 남거나 정리가 충돌하면 안 된다.
                List<Future<?>> closed = new ArrayList<>();
                for (Peer p : peers) closed.add(tasks.submit(() -> { p.close(); return null; }));
                for (Future<?> result : closed) result.get(3, TimeUnit.SECONDS);
                await(() -> f.server.users.getUsers().isEmpty(), "동시 종료 후 사용자 잔존");
            } finally {
                for (Peer p : peers) p.close();
                tasks.shutdownNow();
            }
        }
    }

    private static void loginDeadline() throws Exception {
        try (Fixture f = new Fixture(new RoomManager(), 300, 1000);
             Peer idle = f.connect(); Peer partial = f.connect(); Peer active = f.connect()) {
            active.send("/LOGIN user1 테스터1"); active.expect("/LOGIN_OK user1 테스터1"); active.expect(list1());
            partial.raw("/LOG");
            idle.eof(); partial.eof();
            // 로그인한 정상 사용자는 로그인 대기 제한의 영향을 받지 않는다.
            active.send("/USER_LIST"); active.expect(list1());
        }
    }

    private static void connectionLimit() throws Exception {
        try (Fixture f = new Fixture(new RoomManager(), 10_000, 1000)) {
            List<Peer> peers = new ArrayList<>();
            try {
                for (int i = 0; i < ChatServer.MAX_CLIENTS; i++) peers.add(f.connect());
                MemorySocket extra = new MemorySocket();
                f.listener.pending.add(extra);
                try (Peer rejected = new Peer(extra)) { rejected.eof(); }
            } finally { for (Peer p : peers) p.close(); }
        }
    }

    private static void oversizedFrame() throws Exception {
        try (Fixture f = new Fixture(new RoomManager(), 5000, 1000); Peer p = f.connect()) {
            p.send("x".repeat(8193));
            p.eof();
            // 잘못된 연결 하나를 종료한 뒤에도 새 사용자는 정상 접속할 수 있어야 한다.
            try (Peer healthy = f.connect()) {
                healthy.send("/LOGIN user1 테스터1");
                healthy.expect("/LOGIN_OK user1 테스터1"); healthy.expect(list1());
            }
        }
    }

    private static void slowReceiver() throws Exception {
        try (Fixture f = new Fixture(new RoomManager(), 5000, 1500);
             Peer slow = f.connect(); Peer healthy = f.connect()) {
            slow.send("/LOGIN user1 테스터1"); slow.expect("/LOGIN_OK user1 테스터1"); slow.expect(list1());
            slow.serverSocket.blockWrites = true;
            slow.send("/USER_LIST");
            check(slow.serverSocket.writeStarted.await(2, TimeUnit.SECONDS), "전송 대기 재현 실패");
            healthy.send("/LOGIN user2 테스터2"); healthy.expect("/LOGIN_OK user2 테스터2"); healthy.expect(list2());
            // 느린 연결의 쓰기가 끝나지 않았어도 다른 사용자는 로그인·조회가 가능해야 한다.
            healthy.send("/USER_LIST"); healthy.expect(list2());
            slow.eof(); healthy.expect("/USER_LIST 1 user2 테스터2");
        }
    }

    private static void queueLimit() throws Exception {
        BlockingQueue<ClientHandler> dispatched = new ArrayBlockingQueue<>(1);
        RoomManager rooms = new RoomManager() {
            @Override public void createRoom(ClientHandler client, kr.ac.moyeotalk.common.Message message) {
                dispatched.add(client);
                client.send("/READY");
            }
        };
        try (Fixture f = new Fixture(rooms, 5000, 5000); Peer p = f.connect()) {
            p.send("/LOGIN user1 테스터1"); p.expect("/LOGIN_OK user1 테스터1"); p.expect(list1());
            p.send("/CREATE_ROOM room1 테스트"); p.expect("/READY");
            p.serverSocket.blockWrites = true;
            p.send("/USER_LIST");
            check(p.serverSocket.writeStarted.await(2, TimeUnit.SECONDS), "전송 대기 재현 실패");
            ClientHandler handler = dispatched.poll(2, TimeUnit.SECONDS);
            check(handler != null, "담당 메서드 전달 실패");
            // 네트워크 시간 제한 전에 큐 상한만으로 해당 연결을 종료하는지 검사한다.
            for (int i = 0; i <= ClientHandler.MAX_PENDING_MESSAGES; i++) handler.send("/TEST " + i);
            p.eof();
            await(() -> f.server.users.getUsers().isEmpty(), "큐 초과 연결 정리 실패");
        }
    }

    private static void cleanupFailure() throws Exception {
        RoomManager failing = new RoomManager() {
            @Override public void disconnect(ClientHandler client) {
                throw new IllegalStateException("테스트용 방 정리 실패");
            }
        };
        try (Fixture f = new Fixture(failing, 5000, 1000); Peer a = f.connect(); Peer b = f.connect()) {
            a.send("/LOGIN user1 테스터1"); a.expect("/LOGIN_OK user1 테스터1"); a.expect(list1());
            b.send("/LOGIN user2 테스터2"); b.expect("/LOGIN_OK user2 테스터2"); b.expect(list2()); a.expect(list2());
            f.server.close();
            // 서버 종료 도중 이미 큐에 들어간 목록 알림은 도착할 수 있으므로 끝까지 읽는다.
            while (a.read() != null) {}
            while (b.read() != null) {}
            check(f.server.users.getUsers().isEmpty(), "방 정리 예외 후 사용자 잔존");
        }
    }

    private static String list1() { return "/USER_LIST 1 user1 테스터1"; }
    private static String list2() { return "/USER_LIST 2 user1 테스터1 user2 테스터2"; }
    private static void check(boolean ok, String detail) { if (!ok) throw new AssertionError(detail); }
    private static void await(java.util.function.BooleanSupplier condition, String detail) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(10);
        check(condition.getAsBoolean(), detail);
    }

    /** 실제 accept 루프와 작업 스레드를 실행하고 종료 완료까지 검사한다. */
    private static final class Fixture implements AutoCloseable {
        final MemoryListener listener = new MemoryListener();
        final ChatServer server;
        final ExecutorService runner = Executors.newSingleThreadExecutor();
        final Future<?> serving;
        Fixture(RoomManager rooms, int loginMs, int writeMs) throws IOException {
            server = new ChatServer(listener, rooms, new GameService(), loginMs, writeMs);
            serving = runner.submit(() -> { server.start(); return null; });
        }
        Peer connect() throws Exception {
            MemorySocket socket = new MemorySocket();
            listener.pending.add(socket);
            Peer p = new Peer(socket); p.expect("/WELCOME MOYEOTALK"); return p;
        }
        @Override public void close() throws Exception {
            server.close();
            try {
                serving.get(3, TimeUnit.SECONDS);
                check(server.awaitTermination(3, TimeUnit.SECONDS), "서버 작업 스레드가 남아 있음");
            } finally { runner.shutdownNow(); }
        }
    }

    /** 메모리 대기열로 accept와 연결 해제를 재현한다. 실제 포트는 열지 않는다. */
    private static final class MemoryListener extends ServerSocket {
        final BlockingQueue<MemorySocket> pending = new LinkedBlockingQueue<>();
        volatile boolean stopped;
        MemoryListener() throws IOException {}
        @Override public Socket accept() throws IOException {
            try {
                while (!stopped) {
                    MemorySocket socket = pending.poll(50, TimeUnit.MILLISECONDS);
                    if (socket != null) return socket;
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            throw new SocketException("테스트 리스너 종료");
        }
        @Override public void close() { stopped = true; }
        @Override public int getLocalPort() { return 0; }
    }

    /** close가 막힌 읽기·쓰기를 해제하는 소켓 동작을 재현한다. */
    private static final class MemorySocket extends Socket {
        final Pipe input = new Pipe();
        final Pipe output = new Pipe();
        final CountDownLatch writeStarted = new CountDownLatch(1);
        final CountDownLatch releaseWrite = new CountDownLatch(1);
        volatile boolean blockWrites;
        volatile boolean stopped;
        @Override public InputStream getInputStream() { return input; }
        @Override public OutputStream getOutputStream() {
            return new OutputStream() {
                @Override public void write(int value) throws IOException {
                    write(new byte[] {(byte)value});
                }
                @Override public void write(byte[] data, int off, int len) throws IOException {
                    if (blockWrites) {
                        writeStarted.countDown();
                        try { releaseWrite.await(); }
                        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
                    }
                    if (stopped) throw new SocketException("테스트 연결 종료");
                    for (int i = off; i < off + len; i++) output.bytes.add(data[i] & 255);
                }
            };
        }
        @Override public synchronized void close() {
            if (stopped) return;
            stopped = true; input.close(); output.close(); releaseWrite.countDown();
        }
    }

    private static final class Pipe extends InputStream {
        final BlockingQueue<Integer> bytes = new LinkedBlockingQueue<>();
        boolean eof;
        @Override public int read() throws IOException {
            if (eof) return -1;
            try {
                Integer value = bytes.poll(3, TimeUnit.SECONDS);
                if (value == null) throw new SocketTimeoutException("테스트 읽기 시간 초과");
                if (value == -1) eof = true;
                return value;
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
        }
        // 기본 구현은 버퍼 전체를 기다리므로 한 번에 현재 도착한 바이트만 반환한다.
        @Override public int read(byte[] b, int off, int len) throws IOException {
            if (len == 0) return 0;
            int first = read();
            if (first == -1) return -1;
            b[off] = (byte)first;
            int n = 1;
            while (n < len) {
                Integer next = bytes.poll();
                if (next == null) break;
                if (next == -1) { eof = true; break; }
                b[off + n++] = (byte)(int)next;
            }
            return n;
        }
        @Override public void close() { bytes.offer(-1); }
    }

    private static final class Peer implements AutoCloseable {
        final MemorySocket serverSocket;
        final BufferedReader reader;
        Peer(MemorySocket socket) {
            serverSocket = socket;
            reader = new BufferedReader(new InputStreamReader(socket.output, StandardCharsets.UTF_8));
        }
        void raw(String text) { for (byte b : text.getBytes(StandardCharsets.UTF_8)) serverSocket.input.bytes.add(b & 255); }
        void send(String line) { raw(line + "\n"); }
        String read() throws IOException { return reader.readLine(); }
        void expect(String expected) throws IOException {
            String actual = read(); check(expected.equals(actual), "예상=" + expected + ", 실제=" + actual);
        }
        void error(String code) throws IOException {
            String actual = read(); check(actual != null && actual.startsWith("/ERROR " + code + " "), actual);
        }
        void eof() throws IOException { check(read() == null, "연결이 종료되지 않음"); }
        @Override public void close() { serverSocket.close(); }
    }
}

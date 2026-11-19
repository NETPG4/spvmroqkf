package kr.ac.moyeotalk.server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import kr.ac.moyeotalk.server.user.UserManager;
import kr.ac.moyeotalk.server.chat.RoomManager;
import kr.ac.moyeotalk.server.game.GameService;

/** 접속을 받고 연결마다 수신·전송 작업을 배정하는 서버 실행 클래스. */
public final class ChatServer implements AutoCloseable {
    public static final int DEFAULT_PORT = 5000;
    public static final int MAX_CLIENTS = 50;
    // 로그인 전에는 접속 후 30초, 전송 한 건과 종료 응답에는 5초를 허용한다.
    private static final int LOGIN_TIMEOUT_MS = 30_000;
    private static final int WRITE_TIMEOUT_MS = 5_000;
    final long loginTimeoutNanos;
    final long writeTimeoutNanos;
    final UserManager users = new UserManager();
    final RoomManager rooms;
    final GameService games;
    private final ServerSocket listener;
    private final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();
    // 소켓 쓰기는 별도 작업으로 분리하되 각 작업의 최대 수는 연결 제한과 같다.
    private final ExecutorService readers = newClientPool();
    private final ExecutorService writers = newClientPool();
    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean closed;

    // 종료와 재접속이 겹쳐도 작업 대기열이 무제한으로 늘어나지 않게 한다.
    private static ExecutorService newClientPool() {
        return new ThreadPoolExecutor(MAX_CLIENTS, MAX_CLIENTS, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(MAX_CLIENTS));
    }

    public ChatServer(int port) throws IOException { this(port, new RoomManager(), new GameService()); }

    public ChatServer(int port, RoomManager rooms, GameService games) throws IOException {
        this(new ServerSocket(port), rooms, games, LOGIN_TIMEOUT_MS, WRITE_TIMEOUT_MS);
    }

    // 같은 패키지의 테스트에서 소켓 대역과 짧은 제한 시간을 주입할 수 있다.
    ChatServer(ServerSocket listener, RoomManager rooms, GameService games, int loginMs, int writeMs) {
        this.listener = Objects.requireNonNull(listener);
        this.rooms = Objects.requireNonNull(rooms);
        this.games = Objects.requireNonNull(games);
        if (loginMs <= 0 || writeMs <= 0) throw new IllegalArgumentException("제한 시간은 양수여야 합니다");
        loginTimeoutNanos = TimeUnit.MILLISECONDS.toNanos(loginMs);
        writeTimeoutNanos = TimeUnit.MILLISECONDS.toNanos(writeMs);
        // 전송이 막혀도 이 검사 스레드는 소켓을 닫아 입출력 대기를 해제할 수 있다.
        watchdog.scheduleWithFixedDelay(() -> {
            long now = System.nanoTime();
            for (ClientHandler client : clients) client.checkTimeouts(now);
        }, 100, 100, TimeUnit.MILLISECONDS);
    }

    public int getPort() { return listener.getLocalPort(); }

    /** 새 접속을 기다리며, 등록과 서버 종료의 상태 변경은 같은 잠금으로 보호한다. */
    public void start() throws IOException {
        System.out.println("모여톡 서버 실행: " + getPort());
        while (!closed) {
            try {
                Socket socket = listener.accept();
                synchronized (this) {
                    if (closed || clients.size() >= MAX_CLIENTS) {
                        socket.close();
                        continue;
                    }
                    ClientHandler client = null;
                    try {
                        client = new ClientHandler(socket, this);
                        clients.add(client);
                        writers.execute(client::writeMessages);
                        readers.execute(client);
                    } catch (IOException | RejectedExecutionException e) {
                        if (client != null) client.close();
                        else socket.close();
                        System.err.println("접속 준비 실패: " + e);
                    }
                }
            } catch (SocketException e) {
                // 서버 종료 때문에 accept()가 깨어난 경우는 정상 종료다.
                if (!closed) throw e;
            }
        }
    }

    void remove(ClientHandler client) { clients.remove(client); }

    /** 모든 연결을 정리하고, 한 연결의 정리 실패가 나머지 종료를 막지 않게 한다. */
    @Override public void close() {
        List<ClientHandler> snapshot;
        synchronized (this) {
            if (closed) return;
            closed = true;
            snapshot = List.copyOf(clients);
        }
        try {
            try { listener.close(); }
            catch (IOException e) { System.err.println("서버 소켓 종료 실패: " + e); }
            for (ClientHandler client : snapshot) {
                try { client.close(); }
                catch (RuntimeException e) { System.err.println("연결 정리 실패: " + e); }
            }
        } finally {
            watchdog.shutdownNow();
            readers.shutdownNow();
            writers.shutdownNow();
        }
    }

    // 테스트에서 종료 요청뿐 아니라 실제 작업 스레드 종료까지 확인한다.
    boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        for (ExecutorService pool : List.of(readers, writers, watchdog)) {
            if (!pool.awaitTermination(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)) return false;
        }
        return true;
    }

    public static void main(String[] args) throws IOException {
        int port = args.length == 0 ? DEFAULT_PORT : Integer.parseInt(args[0]);
        try (ChatServer server = new ChatServer(port)) {
            Runtime.getRuntime().addShutdownHook(new Thread(server::close));
            server.start();
        }
    }
}

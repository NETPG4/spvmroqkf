package kr.ac.moyeotalk.server;

import java.io.*;
import java.net.*;
import java.util.Set;
import java.util.concurrent.*;
import kr.ac.moyeotalk.server.user.UserManager;
import kr.ac.moyeotalk.server.chat.RoomManager;
import kr.ac.moyeotalk.server.game.GameService;

public final class ChatServer implements AutoCloseable {
    public static final int DEFAULT_PORT = 5000;
    public static final int MAX_CLIENTS = 50;
    final UserManager users = new UserManager();
    final RoomManager rooms;
    final GameService games;
    private final ServerSocket listener;
    private final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();
    private final ExecutorService workers = Executors.newFixedThreadPool(MAX_CLIENTS);
    private volatile boolean closed;

    public ChatServer(int port) throws IOException { this(port, new RoomManager(), new GameService()); }
    public ChatServer(int port, RoomManager rooms, GameService games) throws IOException {
        this.rooms = rooms;
        this.games = games;
        listener = new ServerSocket(port);
    }
    public int getPort() { return listener.getLocalPort(); }
    public void start() throws IOException {
        System.out.println("모여톡 서버 실행: " + getPort());
        while (!closed) {
            try {
                Socket socket = listener.accept();
                synchronized (this) {
                    if (closed || clients.size() >= MAX_CLIENTS) { socket.close(); continue; }
                    try {
                        ClientHandler client = new ClientHandler(socket, this);
                        clients.add(client);
                        workers.execute(client);
                    } catch (IOException e) { socket.close(); }
                }
            } catch (SocketException e) { if (!closed) throw e; }
        }
    }
    void remove(ClientHandler client) { clients.remove(client); }
    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        try { listener.close(); } catch (IOException ignored) {}
        for (ClientHandler client : clients) client.close();
        workers.shutdownNow();
    }
    public static void main(String[] args) throws IOException {
        int port = args.length == 0 ? DEFAULT_PORT : Integer.parseInt(args[0]);
        try (ChatServer server = new ChatServer(port)) {
            Runtime.getRuntime().addShutdownHook(new Thread(server::close));
            server.start();
        }
    }
}

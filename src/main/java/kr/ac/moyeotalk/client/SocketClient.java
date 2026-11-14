package kr.ac.moyeotalk.client;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import kr.ac.moyeotalk.common.WireCodec;

/** GUI에서도 재사용할 수 있는 소켓 입출력 클래스. receive는 수신 스레드에서 호출한다. */
public final class SocketClient implements AutoCloseable {
    private final Socket socket;
    private final BufferedReader reader;
    private final BufferedWriter writer;
    public SocketClient(String host, int port) throws IOException {
        socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), 5000);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        } catch (IOException e) { socket.close(); throw e; }
    }
    public synchronized void send(String line) throws IOException {
        WireCodec.validate(line);
        writer.write(line); writer.newLine(); writer.flush();
    }
    public String receive() throws IOException { return WireCodec.readLine(reader); }
    @Override public void close() throws IOException { socket.close(); }
}

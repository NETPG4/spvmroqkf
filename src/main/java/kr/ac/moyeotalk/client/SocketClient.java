package kr.ac.moyeotalk.client;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import kr.ac.moyeotalk.common.WireCodec;

/** 서버와 연결하고 문자열을 주고받는다. AutoCloseable은 자동 연결 종료를 지원한다. */
public final class SocketClient implements AutoCloseable {
    // 서버와 통신하는 연결. final 필드는 생성 후 다른 객체로 바꿀 수 없다.
    private final Socket socket;
    // 서버에서 받은 바이트를 문자로 읽는 입력 통로.
    private final BufferedReader reader;
    // 문자열을 서버로 보내는 출력 통로.
    private final BufferedWriter writer;
    // 서버 연결은 최대 5초 기다리며, 한글 전송에는 UTF-8을 사용한다.
    public SocketClient(String host, int port) throws IOException {
        // 아직 서버에 연결되지 않은 소켓을 만든다.
        socket = new Socket();
        try {
            // 주소와 포트로 접속한다. 5000은 연결 대기 제한 시간인 5000밀리초다.
            socket.connect(new InetSocketAddress(host, port), 5000);
            // 서버가 보내는 UTF-8 바이트를 문자로 변환한다.
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            // 보낼 문자를 UTF-8 바이트로 변환한다.
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        // 연결 준비에 실패하면 소켓을 정리하고 호출한 곳으로 오류를 전달한다.
        } catch (IOException e) { socket.close(); throw e; }
    }
    // synchronized는 여러 스레드가 보내는 내용이 서로 섞이지 않게 한다.
    public synchronized void send(String line) throws IOException {
        // 전송 전에 길이 제한과 메시지 안의 줄바꿈 여부를 검사한다.
        WireCodec.validate(line);
        // 줄바꿈으로 메시지 끝을 표시하고 flush()로 즉시 전송한다.
        writer.write(line); writer.write('\n'); writer.flush();
    }
    // 메시지가 올 때까지 기다리므로 GUI에서는 별도 수신 스레드에서 호출한다.
    public String receive() throws IOException { return WireCodec.readLine(reader); }
    // 소켓을 닫으면 연결된 입력·출력 통로도 함께 닫힌다.
    @Override public void close() throws IOException { socket.close(); }
}

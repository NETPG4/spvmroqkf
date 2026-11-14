package kr.ac.moyeotalk.client;

import java.io.*;
import java.nio.charset.StandardCharsets;

/** A 기능 점검용 콘솔 클라이언트. 최종 GUI는 별도 연결한다. */
public final class ChatClient {
    public static void main(String[] args) throws IOException {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 5000;
        try (SocketClient client = new SocketClient(host, port)) {
            Thread input = new Thread(() -> {
                try {
                    BufferedReader console = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                    String line;
                    while ((line = console.readLine()) != null) client.send(line);
                    client.close();
                } catch (IOException e) { try { client.close(); } catch (IOException ignored) {} }
            }, "console-input");
            input.setDaemon(true);
            input.start();
            System.out.println("명령어 예시: /LOGIN user1 정환, /USER_LIST, /LOGOUT");
            String line;
            while ((line = client.receive()) != null) System.out.println(line);
            System.out.println("서버 연결 종료");
        }
    }
}

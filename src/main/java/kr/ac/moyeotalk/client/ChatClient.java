package kr.ac.moyeotalk.client;

import java.io.*;
import java.nio.charset.StandardCharsets;

/** 키보드로 명령어를 보내고 서버 응답을 화면에 출력하는 실행 클래스. */
public final class ChatClient {
    // 프로그램의 시작점. 실행 인자로 서버 주소와 포트를 받을 수 있다.
    public static void main(String[] args) throws IOException {
        // 주소를 생략하면 현재 컴퓨터를 뜻하는 localhost에 연결한다.
        String host = args.length > 0 ? args[0] : "localhost";
        // 포트를 생략하면 5000을 사용하고, 입력값은 문자열에서 정수로 바꾼다.
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 5000;
        // 괄호 안의 연결은 정상 종료나 예외 발생 시 자동으로 닫힌다.
        try (SocketClient client = new SocketClient(host, port)) {
            // 입력을 기다리는 동안에도 서버 응답을 받도록 별도 스레드를 사용한다.
            Thread input = new Thread(() -> {
                try {
                    // 키보드 입력 바이트를 한글 문자열로 바꾸고 줄 단위로 읽는다.
                    BufferedReader console = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                    readConsole(console, client::send, System.err);
                    // 키보드 입력이 끝나면 서버 연결도 종료한다.
                    client.close();
                // 입출력에 실패하면 연결을 닫는다. 종료 도중의 추가 오류는 무시한다.
                } catch (IOException e) { try { client.close(); } catch (IOException ignored) {} }
            }, "console-input");
            // 일반 스레드가 모두 끝나면 키보드 입력 대기가 프로그램 종료를 막지 않게 한다.
            input.setDaemon(true);
            // 생성한 입력 스레드를 실제로 시작한다.
            input.start();
            System.out.println("명령어 예시: /LOGIN user1 테스터1, /USER_LIST, /LOGOUT");
            String line;
            // 서버가 연결을 닫으면 receive()가 null을 반환하여 반복을 끝낸다.
            while ((line = client.receive()) != null) System.out.println(line);
            System.out.println("서버 연결 종료");
        }
    }

    /** 입력 오류는 해당 줄만 건너뛰고, 통신 오류는 호출자에게 전달해 연결을 정리한다. */
    static void readConsole(BufferedReader console, LineSender sender, PrintStream errors) throws IOException {
        String line;
        while ((line = console.readLine()) != null) {
            try { sender.send(line); }
            catch (IllegalArgumentException e) { errors.println("입력 오류: " + e.getMessage()); }
        }
    }

    // 소켓 전송의 입출력 예외를 유지하면서 입력 처리만 독립적으로 검증할 수 있게 한다.
    @FunctionalInterface
    interface LineSender { void send(String line) throws IOException; }

}

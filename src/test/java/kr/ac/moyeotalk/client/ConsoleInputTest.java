package kr.ac.moyeotalk.client;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import kr.ac.moyeotalk.common.WireCodec;

/** 긴 입력 다음의 정상 명령어가 누락되지 않고 통신 실패는 호출자에게 전달되는지 검사한다. */
public final class ConsoleInputTest {
    public static void main(String[] args) throws Exception {
        List<String> sent = new ArrayList<>();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        try (PrintStream output = new PrintStream(errors, true, StandardCharsets.UTF_8)) {
            ChatClient.readConsole(new BufferedReader(new StringReader("x".repeat(8193) + "\n/USER_LIST\n/QUIT\n")),
                    line -> { WireCodec.validate(line); sent.add(line); }, output);
            if (!sent.equals(List.of("/USER_LIST", "/QUIT"))) throw new AssertionError(sent);
            if (!errors.toString(StandardCharsets.UTF_8).contains("입력 오류:")) throw new AssertionError("오류 안내 누락");
            try {
                ChatClient.readConsole(new BufferedReader(new StringReader("/QUIT\n")),
                        line -> { throw new IOException("테스트 통신 실패"); }, output);
                throw new AssertionError("통신 실패를 무시함");
            } catch (IOException expected) {
                if (!expected.getMessage().equals("테스트 통신 실패")) throw expected;
            }
        }
        System.out.println("PASS: 긴 콘솔 입력 후 정상 명령어 처리 및 통신 예외 전달");
    }
}

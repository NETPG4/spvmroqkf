package kr.ac.moyeotalk.common;

import java.io.*;

/** 한 줄을 메시지 하나로 읽고, 길이와 줄바꿈 규칙을 검사한다. */
public final class WireCodec {
    // 메시지 길이의 상한. 바이트 수가 아니라 자바 문자열의 길이를 기준으로 한다.
    public static final int MAX_LINE_LENGTH = 8192;
    // 객체를 만들지 않고 static 메서드만 사용하는 도구 클래스다.
    private WireCodec() {}
    // LF는 줄바꿈, CRLF는 Windows에서 주로 쓰는 줄바꿈 방식이다.
    public static String readLine(Reader reader) throws IOException {
        // 읽은 문자를 차례로 쌓아 한 줄을 만든다.
        StringBuilder line = new StringBuilder();
        // 문자 값뿐 아니라 입력 종료를 나타내는 -1도 저장하기 위해 정수를 사용한다.
        int ch;
        // 한 문자씩 읽는다. 더 읽을 내용이 없으면 -1이 반환된다.
        while ((ch = reader.read()) != -1) {
            // 줄바꿈 문자를 만나면 메시지 한 개를 다 읽은 것이다.
            if (ch == '\n') {
                // 줄 끝에 붙은 캐리지 리턴을 제거하여 두 종류의 줄바꿈을 동일하게 처리한다.
                if (!line.isEmpty() && line.charAt(line.length() - 1) == '\r') line.setLength(line.length() - 1);
                // 문자를 쌓던 객체를 일반 문자열로 바꾸어 반환한다.
                return line.toString();
            }
            // 문자를 추가하기 전에 길이를 검사하여 지나치게 긴 입력을 막는다.
            if (line.length() >= MAX_LINE_LENGTH) throw new IOException("메시지 길이 초과");
            // 읽은 정수 값을 문자로 바꾸어 현재 메시지 끝에 붙인다.
            line.append((char) ch);
        }
        return null; // 개행 없이 끊긴 불완전한 메시지는 실행하지 않는다.
    }
    // 내용에 줄바꿈을 넣어 여러 명령어를 한꺼번에 보내는 것을 막는다.
    public static void validate(String line) {
        // 전송할 내용이 길이 제한을 넘거나 줄바꿈을 포함하면 예외를 발생시킨다.
        if (line.length() > MAX_LINE_LENGTH || line.indexOf('\n') >= 0 || line.indexOf('\r') >= 0)
            throw new IllegalArgumentException("한 줄 메시지만 전송할 수 있습니다");
    }
}

package kr.ac.moyeotalk.common;

import java.io.*;

/** UTF-8 텍스트 한 줄이 메시지 하나다. CRLF와 LF를 모두 받는다. */
public final class WireCodec {
    public static final int MAX_LINE_LENGTH = 8192;
    private WireCodec() {}
    public static String readLine(Reader reader) throws IOException {
        StringBuilder line = new StringBuilder();
        int ch;
        while ((ch = reader.read()) != -1) {
            if (ch == '\n') {
                if (!line.isEmpty() && line.charAt(line.length() - 1) == '\r') line.setLength(line.length() - 1);
                return line.toString();
            }
            if (line.length() >= MAX_LINE_LENGTH) throw new IOException("메시지 길이 초과");
            line.append((char) ch);
        }
        return null; // 개행 없이 끊긴 불완전한 메시지는 실행하지 않는다.
    }
    public static void validate(String line) {
        if (line.length() > MAX_LINE_LENGTH || line.indexOf('\n') >= 0 || line.indexOf('\r') >= 0)
            throw new IllegalArgumentException("한 줄 메시지만 전송할 수 있습니다");
    }
}

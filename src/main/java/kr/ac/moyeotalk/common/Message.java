package kr.ac.moyeotalk.common;

/** 명령어와 원문 인자를 분리한다. 본문의 공백은 보존한다. */
public record Message(String command, String arguments) {
    public static Message parse(String line) {
        if (line == null || !line.startsWith("/"))
            throw new IllegalArgumentException("명령어는 /로 시작해야 합니다");
        int space = line.indexOf(' ');
        String command = space < 0 ? line.substring(1) : line.substring(1, space);
        if (!command.matches("[A-Z_]+")) throw new IllegalArgumentException("잘못된 명령어입니다");
        return new Message(command, space < 0 ? "" : line.substring(space + 1));
    }

    /** 마지막 필드는 남은 문자열 전체이므로 채팅 본문에 공백을 넣을 수 있다. */
    public String[] fields(int count) {
        String[] fields = arguments.split(" +", count);
        if (fields.length != count) throw new IllegalArgumentException("인자 수를 확인하세요");
        for (String field : fields) if (field.isBlank()) throw new IllegalArgumentException("빈 인자는 사용할 수 없습니다");
        return fields;
    }
    public void noArguments() {
        if (!arguments.isBlank()) throw new IllegalArgumentException("인자가 없는 명령어입니다");
    }
}

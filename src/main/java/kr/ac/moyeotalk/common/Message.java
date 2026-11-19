package kr.ac.moyeotalk.common;

/** 명령어와 인자를 담는 record. 저장한 값을 읽는 메서드가 자동으로 만들어진다. */
// command는 /를 제외한 명령어 이름, arguments는 명령어 뒤에 전달된 문자열이다.
public record Message(String command, String arguments) {
    // 예: /LOGIN user1 테스터1 → command는 LOGIN, arguments는 user1 테스터1.
    public static Message parse(String line) {
        // 입력이 없거나 명령어 시작 기호가 빠진 경우 처리를 중단한다.
        if (line == null || !line.startsWith("/"))
            throw new IllegalArgumentException("명령어는 /로 시작해야 합니다");
        // 첫 공백의 위치를 찾는다. 공백이 없으면 결과는 -1이다.
        int space = line.indexOf(' ');
        // 앞의 /를 제외하고 첫 공백 전까지를 명령어로 꺼낸다.
        String command = space < 0 ? line.substring(1) : line.substring(1, space);
        // 명령어 이름에는 영문 대문자와 밑줄만 허용한다.
        if (!command.matches("[A-Z_]+")) throw new IllegalArgumentException("잘못된 명령어입니다");
        // 공백 뒤의 인자는 원문 그대로 저장한다. 공백이 없으면 빈 문자열을 저장한다.
        return new Message(command, space < 0 ? "" : line.substring(space + 1));
    }

    /** 마지막 필드는 남은 문자열 전체이므로 채팅 본문에 공백을 넣을 수 있다. */
    public String[] fields(int count) {
        // 연속된 공백을 구분자로 쓰되 최대 count개로 나눈다. 마지막 값은 더 나누지 않는다.
        String[] fields = arguments.split(" +", count);
        // 요청한 개수만큼 값이 없으면 잘못된 요청으로 처리한다.
        if (fields.length != count) throw new IllegalArgumentException("인자 수를 확인하세요");
        // 각 값이 비어 있거나 공백뿐인지 검사한다.
        for (String field : fields) if (field.isBlank()) throw new IllegalArgumentException("빈 인자는 사용할 수 없습니다");
        // 검사를 통과한 값들을 배열로 반환한다.
        return fields;
    }
    // /USER_LIST처럼 뒤에 값을 받지 않는 명령어를 검사한다.
    public void noArguments() {
        // 인자가 비어 있거나 공백뿐이면 허용하고, 실제 값이 있으면 거부한다.
        if (!arguments.isBlank()) throw new IllegalArgumentException("인자가 없는 명령어입니다");
    }
}

package kr.ac.moyeotalk.server.user;

/** 로그인 ID와 닉네임을 저장한다. record는 생성자와 값 조회 메서드를 자동 제공한다. */
// userId는 중복 로그인 확인용 식별자이고, nickname은 화면에 표시할 이름이다.
// 생성 후 값을 변경할 수 없으며 userId(), nickname()으로 저장된 값을 읽는다.
public record User(String userId, String nickname) {}

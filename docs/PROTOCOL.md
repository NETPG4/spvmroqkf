# 문자열 통신 규칙

팀에서 확정한 사항은 공백 구분 문자열 방식과 `/명령어` 접두사입니다. 아래의 LOGIN 인자, 응답 이름, 목록 형태는 이 베이스 코드에서 제안·구현한 세부 규격입니다. 팀원이 구현을 시작하기 전에 함께 확인합니다.

TCP 위에 UTF-8 텍스트를 사용합니다. LF로 끝나는 한 줄이 메시지 하나이며 CRLF도 받습니다. Java ObjectInputStream 또는 writeUTF 방식과 혼용하지 않습니다. 명령어는 대문자이고 ID·닉네임에는 공백을 넣지 않습니다. 인자 구분자는 ASCII 공백입니다. 마지막 본문 필드는 남은 문자열 전체를 사용해 내부 공백을 보존합니다. 개행을 포함한 본문은 지원하지 않습니다. 최대 프레임은 개행 전 8192 Java 문자(CRLF 사용 시 CR 포함)입니다.

## A 구현 명령어

| 요청 | 의미 | 직접 응답 |
|---|---|---|
| `/LOGIN userId nickname` | 접속 ID 등록 | `/LOGIN_OK userId nickname` |
| `/USER_LIST` | 접속자 조회 | `/USER_LIST count userId nickname ...` |
| `/LOGOUT` | 로그아웃 후 TCP 종료 | `/LOGOUT_OK` |
| `/QUIT` | 로그인 전에도 연결 종료 가능 | `/LOGOUT_OK` |

서버 접속 직후 `/WELCOME MOYEOTALK`를 보냅니다. ID는 영문·숫자·밑줄·하이픈 1~32자, 닉네임은 공백 없이 1~32자입니다. 닉네임 중복은 허용하고 ID 중복은 금지합니다. 로그인한 연결에서는 ID를 바꿀 수 없습니다. 로그아웃 후 새 연결에서 다시 로그인합니다.

로그인 성공 응답 다음에 전체 접속자에게 최신 목록을 보냅니다. 연결이 끊겨도 남아 있는 접속자에게 목록을 보냅니다. 목록은 ID 순서입니다. 클라이언트는 요청 응답뿐 아니라 수시로 오는 목록 알림도 처리해야 합니다.

오류는 `/ERROR code 설명`입니다. 주요 코드는 `BAD_REQUEST`, `LOGIN_REQUIRED`, `DUPLICATE_USER`, `ALREADY_LOGGED_IN`, `UNKNOWN_COMMAND`, `SENDER_MISMATCH`, `NOT_IMPLEMENTED`입니다. 일반 명령어 오류 후에는 연결을 유지합니다. 메시지 길이 초과, 통신 오류는 연결을 종료합니다. 50개 연결이 차면 새 연결을 종료합니다.

## B·C 연결 지점

| 명령어 | 호출 메서드 | 현재 상태 |
|---|---|---|
| CREATE_ROOM | RoomManager.createRoom | B 구현 예정 |
| JOIN_ROOM | RoomManager.joinRoom | B 구현 예정 |
| LEAVE_ROOM | RoomManager.leaveRoom | B 구현 예정 |
| DELETE_ROOM | RoomManager.deleteRoom | B 구현 예정 |
| CHAT | RoomManager.chat | 인자·발신자 검사만 구현 |
| DELETE_MESSAGE | RoomManager.deleteMessage | B 구현 예정 |
| READ_MESSAGE | RoomManager.readMessage | B 구현 예정 |
| GAME_RECORD, COIN_UPDATE, EMOTICON_SEND | GameService.handle | C 구현 예정 |

모든 요청에는 `/`를 붙입니다. CHAT 형식은 합의 예시인 `/CHAT room1 user1 안녕 하세요`를 따릅니다. 서버는 senderId가 로그인 ID와 일치하는지 확인합니다. B는 추가로 방 존재 여부와 참여자 여부를 검사하고 수신자에게 전달해야 합니다. 나머지 B·C 명령어의 인자와 성공 응답은 각 담당자가 합의 후 이 문서에 추가합니다. 현재는 성공을 가장하지 않고 `NOT_IMPLEMENTED`를 반환합니다.

코인 증가는 추후 C가 서버에서 검증한 게임 결과를 근거로 처리해야 합니다. 현재 COIN_UPDATE는 클라이언트가 잔액을 변경하는 기능을 제공하지 않습니다.

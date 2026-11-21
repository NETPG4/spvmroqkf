# 서버와 팀 기능 연결

ChatServer가 ServerSocket으로 접속을 수락하고 연결마다 ClientHandler를 생성합니다. 최대 50개 연결을 처리하며 수신·전송 풀은 각각 최대 50개 스레드와 50개 대기 작업으로 제한합니다. 별도 검사 스레드 하나가 로그인·전송 제한 시간을 확인합니다. 모든 핸들러가 같은 UserManager, RoomManager, GameService를 공유합니다.

ClientHandler는 수신 문자열을 Message로 파싱하고 로그인 여부를 검사한 뒤 switch에서 담당 메서드 한 줄만 호출합니다. 기능 로직은 switch에 넣지 않습니다. 예를 들어 CREATE_ROOM은 `server.rooms.createRoom(this, message)`로 전달합니다.

B는 `public void createRoom(ClientHandler client, Message message)`처럼 기존 서명을 유지하면서 RoomManager를 구현합니다. `client.getUser()`로 서버가 확인한 사용자 정보를 얻고, `message.fields(n)`으로 고정 인자와 마지막 본문을 분리하며, `client.send("/응답 ...")` 또는 `client.error(code, detail)`로 응답합니다. B·C 서비스는 여러 스레드가 동시에 호출하므로 방·게임 상태의 동기화가 필요합니다.

클라이언트가 끊기면 ClientHandler.close가 소켓을 닫고 RoomManager.disconnect, UserManager.disconnect를 호출합니다. close는 여러 번 호출되어도 정리 작업을 한 번만 실행합니다. B는 disconnect에서 해당 사용자를 모든 방에서 제거해야 하며 로그인하지 않은 연결도 종료될 수 있으므로 getUser()의 null을 처리해야 합니다. 서비스의 전송은 ClientHandler.send를 통해 수행하고 소켓 출력 스트림을 직접 공유하지 않습니다.

UserManager는 등록·중복 검사·해제·목록 알림의 큐 등록 순서를 같은 잠금으로 보호합니다. `send`는 연결별 최대 128개 메시지 큐에 넣고 즉시 반환하며 실제 소켓 쓰기는 전송 작업 하나가 순서대로 수행합니다. 따라서 느린 수신자의 전송 대기가 사용자 관리 잠금을 점유하지 않습니다. 큐가 가득 차면 해당 소켓을 닫고 수신·전송 작업의 종료 경로에서 목록을 정리합니다. 큐 등록 중에 직접 사용자 목록을 변경하지 않으므로 목록 순회 중 재진입도 막습니다. getUsers는 사용자 정보의 스냅샷을 반환합니다.

로그아웃은 `sendAndClose`로 마지막 응답을 등록하고 이전 응답까지 전송한 다음 닫습니다. 일반 `close`는 즉시 연결을 닫고 미전송 메시지를 버립니다. 로그인 전에는 접속 후 30초, 전송 한 건과 정상 종료 대기는 각각 5초가 한도입니다. 100밀리초 간격으로 검사하며, 부분 입력을 계속 보내도 로그인 마감은 연장하지 않습니다. 서버 종료는 새 접속을 막고 연결별 정리를 수행한 뒤 세 작업 풀을 종료합니다. 방 정리에서 실행 예외가 나더라도 사용자 제거와 나머지 연결 정리를 계속합니다.

ChatClient는 콘솔 입력용 데몬 스레드와 수신 루프를 사용합니다. 긴 입력은 오류를 안내하고 다음 입력을 받으며, 통신 실패는 연결 종료로 처리합니다. GUI 담당자는 SocketClient를 재사용하고 수신을 UI 스레드 밖에서 실행한 뒤 화면 갱신을 UI 스레드로 전달하면 됩니다.

StarterTest는 실제 TCP로 A 기능을 확인하고 RoomManager를 주입해 한 줄 위임이 작동하는지도 검사합니다. B·C 기능 자체의 완료 여부를 확인하는 테스트는 아닙니다. ConsoleInputTest는 긴 입력 후 복구를, ReliabilityTest는 포트 없는 소켓 대역으로 동시 로그인·목록 순서·재접속·느린 전송·시간 제한·종료 실패와 스레드 정리를 검사합니다. 실제 TCP 결과와 구분하여 기록합니다.

## 12주차 B 통합 상태

PR #1에서 RoomManager에 방 생성·입장·퇴장, 채팅과 읽음 처리가 추가되었습니다. ChatRoom은 참여자와 최근 200개 메시지를 보관하고, ChatMessage는 메시지 번호·발신자·본문·읽은 사용자 ID를 보관합니다. 두 상태 클래스는 RoomManager의 synchronized 메서드 안에서 사용하며 상태 변경과 알림 큐 등록 순서를 보호합니다. 실제 소켓 출력은 기존 ClientHandler 전송 작업이 수행합니다. disconnect는 모든 참여 방에서 연결을 제거하고 퇴장 알림을 등록합니다. 방·메시지 삭제와 공지는 아직 미구현입니다.

RoomTest는 실제 TCP로 세 사용자의 방·채팅·읽음·강제 연결 종료를 검사합니다. 실행 명령과 알려진 한계는 [B 병합 검증 기록](B_VERIFICATION.md)에 기록합니다.

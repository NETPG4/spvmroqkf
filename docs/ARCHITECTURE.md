# 서버와 팀 기능 연결

ChatServer가 ServerSocket으로 접속을 수락하고 연결마다 ClientHandler를 생성합니다. 고정 크기 스레드 풀에서 최대 50개 연결을 처리합니다. 모든 핸들러가 같은 UserManager, RoomManager, GameService를 공유합니다.

ClientHandler는 수신 문자열을 Message로 파싱하고 로그인 여부를 검사한 뒤 switch에서 담당 메서드 한 줄만 호출합니다. 기능 로직은 switch에 넣지 않습니다. 예를 들어 CREATE_ROOM은 `server.rooms.createRoom(this, message)`로 전달합니다.

B는 `public void createRoom(ClientHandler client, Message message)`처럼 기존 서명을 유지하면서 RoomManager를 구현합니다. `client.getUser()`로 서버가 확인한 사용자 정보를 얻고, `message.fields(n)`으로 고정 인자와 마지막 본문을 분리하며, `client.send("/응답 ...")` 또는 `client.error(code, detail)`로 응답합니다. B·C 서비스는 여러 스레드가 동시에 호출하므로 방·게임 상태의 동기화가 필요합니다.

클라이언트가 끊기면 ClientHandler.close가 소켓을 닫고 RoomManager.disconnect, UserManager.disconnect를 호출합니다. close는 여러 번 호출되어도 정리 작업을 한 번만 실행합니다. B는 disconnect에서 해당 사용자를 모든 방에서 제거해야 하며 로그인하지 않은 연결도 종료될 수 있으므로 getUser()의 null을 처리해야 합니다. 서비스의 전송은 ClientHandler.send를 통해 수행하고 소켓 출력 스트림을 직접 공유하지 않습니다.

UserManager는 등록·중복 검사·해제·목록 알림을 같은 잠금으로 보호합니다. 소켓 쓰기 실패 시 send에서 소켓만 닫고 수신 스레드의 finally에서 실제 목록을 정리합니다. getUsers는 사용자 정보의 스냅샷을 반환합니다.

ChatClient는 콘솔 입력용 데몬 스레드와 수신 루프를 사용합니다. GUI 담당자는 SocketClient를 재사용하고 수신을 UI 스레드 밖에서 실행한 뒤 화면 갱신을 UI 스레드로 전달하면 됩니다.

StarterTest는 실제 TCP로 A 기능을 확인하고 RoomManager를 주입해 한 줄 위임이 작동하는지도 검사합니다. B·C 기능 자체의 완료 여부를 확인하는 테스트는 아닙니다.

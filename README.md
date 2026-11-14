# 모여톡 A 담당 베이스 코드

Java TCP 소켓 기반 서버·사용자 관리 시작 코드입니다. 합의한 `/명령어 값` 문자열 방식과 공통 설계안의 클래스명을 사용합니다. JDK 17 이상만 있으면 실행할 수 있고 외부 라이브러리는 필요하지 않습니다.

구현 범위는 서버 실행, 다중 클라이언트 연결, ID·닉네임 로그인, 중복 ID 차단, 사용자 목록 조회 및 변경 알림, 로그아웃과 연결 종료 정리입니다. 로그인은 접속 이름 등록이며 비밀번호 인증이나 영구 저장은 없습니다. B의 방·채팅 기능과 C의 게임·코인·이모티콘은 호출 지점만 준비되어 있습니다.

## 실행

macOS/Linux에서 터미널을 각각 열어 실행합니다.

```sh
chmod +x run.sh
./run.sh server 5000
./run.sh client localhost 5000
./run.sh client localhost 5000
```

Windows PowerShell:

```powershell
.\run.ps1 -Mode server -Port 5000
.\run.ps1 -Mode client -HostName localhost -Port 5000
```

첫 번째 클라이언트에 `/LOGIN user1 정환`, 두 번째에 `/LOGIN user2 은서`를 입력합니다. 두 창 모두 `/USER_LIST 2 user1 정환 user2 은서`를 받습니다. `/USER_LIST`로 다시 조회하고 `/LOGOUT`으로 종료합니다. 서버는 Ctrl+C로 종료합니다. 다른 컴퓨터에서 연결하려면 localhost 대신 서버 PC의 LAN IP를 사용하고 해당 포트를 방화벽에서 허용합니다.

## 코드 구조

```text
src/main/java/kr/ac/moyeotalk/
├── client/ChatClient.java       콘솔 실행과 화면 출력
├── client/SocketClient.java     재사용 가능한 송수신
├── common/Message.java          명령어 및 인자 파싱
├── common/WireCodec.java        줄 단위 메시지와 길이 제한
└── server/
    ├── ChatServer.java          접속 수락 및 서버 종료
    ├── ClientHandler.java       사용자별 수신과 한 줄 위임
    ├── user/User.java           사용자 ID·닉네임
    ├── user/UserManager.java    로그인·목록·접속 해제
    ├── chat/RoomManager.java    B 담당 확장 지점
    └── game/GameService.java    C 담당 확장 지점
```

`build/`는 실행 스크립트가 생성하는 산출물이므로 공유 소스에서 제외합니다. 보내주신 트리의 DesktopApp, ClientSession, UserService 등의 기존 소스는 제공되지 않아 복원하지 않았습니다. 문서의 ChatClient, ClientHandler, UserManager 이름을 사용했으며 GUI는 포함하지 않습니다. 교수님 수업 원본 코드도 제공되지 않아 Java 표준 소켓으로 새로 작성했습니다.

## 검증

```sh
./run.sh test
# 소켓을 열 수 없는 환경에서 파서만 점검할 때 (먼저 컴파일 필요)
java -ea -cp build/classes kr.ac.moyeotalk.StarterTest codec
```

Windows에서는 `.\run.ps1 -Mode test`를 실행합니다. 통합 테스트는 임의의 빈 포트를 사용하며 실제 클라이언트 두 개를 연결합니다.

작성 환경에서는 Java 17 대상 컴파일과 파서 테스트를 통과했습니다. TCP 통합 테스트는 환경의 포트 바인딩 제한(`Operation not permitted`)으로 실행하지 못했습니다. 공유 전 일반 터미널에서 `./run.sh test`를 실행해야 합니다. PowerShell 스크립트는 이 macOS 환경에서 실행 검증하지 않았습니다.

이 코드는 수업용 시작점입니다. 최대 연결 수는 50이며, 동기식 전송을 사용하므로 읽지 않는 클라이언트가 있으면 목록 알림이 지연될 수 있습니다. 실제 서비스로 확장할 때는 전송 큐·타임아웃·인증·암호화가 추가로 필요합니다.

팀 연결 방법은 [ARCHITECTURE](docs/ARCHITECTURE.md), 명령어는 [PROTOCOL](docs/PROTOCOL.md), 작업 분담은 [TEAM](docs/TEAM.md)을 참고하세요.

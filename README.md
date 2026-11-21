# 모여톡 서버·채팅방

Java TCP 소켓 기반 서버·사용자 관리 시작 코드입니다. 합의한 `/명령어 값` 문자열 방식과 공통 설계안의 클래스명을 사용합니다. JDK 17 이상만 있으면 실행할 수 있고 외부 라이브러리는 필요하지 않습니다.

개발·검토·제출 준비에는 [필수 작업 규칙](AGENTS.md)과 [제출·평가 기준 및 체크리스트](docs/PROJECT_RULES.md)를 적용합니다. 원본 PDF의 필수·권장·선택 사항과 현재 미완료 항목을 구분해 기록했습니다.

구현 범위는 서버 실행, 다중 클라이언트 연결, ID·닉네임 로그인, 중복 ID 차단, 사용자 목록 조회 및 변경 알림, 로그아웃과 연결 종료 정리입니다. 로그인은 접속 이름 등록이며 비밀번호 인증이나 영구 저장은 없습니다. B의 방 생성·입장·퇴장, 채팅 전달, 메시지 번호와 읽음 처리는 구현되어 있습니다. 방·메시지 삭제, 공지와 C의 게임·코인·이모티콘은 미구현입니다.

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

첫 번째 클라이언트에 `/LOGIN user1 테스터1`, 두 번째에 `/LOGIN user2 테스터2`를 입력합니다. 두 창 모두 `/USER_LIST 2 user1 테스터1 user2 테스터2`를 받습니다. `/USER_LIST`로 다시 조회하고 `/LOGOUT`으로 종료합니다. 서버는 Ctrl+C로 종료합니다. 다른 컴퓨터에서 연결하려면 localhost 대신 서버 PC의 LAN IP를 사용하고 해당 포트를 방화벽에서 허용합니다.

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
    ├── chat/RoomManager.java    방·채팅·읽음 처리 및 동기화
    ├── chat/ChatRoom.java       방 참여자와 최근 200개 메시지
    ├── chat/ChatMessage.java    메시지 내용과 읽은 사용자
    └── game/GameService.java    C 담당 확장 지점
```

`build/`는 실행 스크립트가 생성하는 산출물이므로 공유 소스에서 제외합니다. 보내주신 트리의 DesktopApp, ClientSession, UserService 등의 기존 소스는 제공되지 않아 복원하지 않았습니다. 문서의 ChatClient, ClientHandler, UserManager 이름을 사용했으며 GUI는 포함하지 않습니다. 교수님 수업 원본 코드도 제공되지 않아 Java 표준 소켓으로 새로 작성했습니다.

## 검증

```sh
./run.sh test
# 위 명령의 컴파일 결과로 B의 실제 TCP 테스트도 실행
java -ea -cp build/classes kr.ac.moyeotalk.RoomTest
# 포트를 열지 않고 문자열·입력·서버 처리 로직을 검사할 때
./run.sh test-local
```

Windows에서는 `.\run.ps1 -Mode test`를 실행합니다. 포트 없는 검사는 `-Mode test-local`을 사용합니다. B 테스트는 위의 `java` 명령을 동일하게 실행합니다. 통합 테스트는 임의의 빈 포트를 사용하며 A 테스트는 클라이언트 두 개, B 테스트는 세 개를 연결합니다.

작성 환경에서는 Java 17 대상 컴파일, 문자열 처리, 긴 콘솔 입력 복구, 소켓 대역 기반 서버 신뢰성 테스트를 통과했습니다. 소켓 대역은 실제 서버 처리 코드를 사용하지만 운영체제 TCP 연결을 검증하지는 않습니다. 2026-11-22 PR #1 병합 검증에서는 샌드박스의 포트 바인딩 제한을 확인한 후 승인된 별도 실행으로 A·B 실제 TCP 통합 테스트도 통과했습니다. 상세 결과와 남은 문제는 [B 병합 검증 기록](docs/B_VERIFICATION.md)을 참고하세요. PowerShell 스크립트는 이 macOS 환경에서 실행 검증하지 않았습니다.

이 코드는 수업용 시작점입니다. 최대 연결 수는 50이며, 연결별 전송 대기열은 128개로 제한합니다. 로그인 전 대기는 30초, 전송 한 건과 로그아웃 응답 마무리는 5초로 제한하고 100밀리초 간격으로 검사합니다. 사용자 관리 잠금에서는 메시지를 큐에 넣기만 합니다. 인증·암호화와 실제 네트워크에서의 부하 검증은 포함하지 않습니다.

보완 내용과 검증 근거는 [A 담당 검증 기록](docs/A_VERIFICATION.md)에 정리했습니다.

팀 연결 방법은 [ARCHITECTURE](docs/ARCHITECTURE.md), 명령어는 [PROTOCOL](docs/PROTOCOL.md), 작업 분담은 [TEAM](docs/TEAM.md)을 참고하세요.

11주차의 통신 형식, 명령어 연결, 기준 코드·공유 방법, 공지·이모티콘 담당 합의는 [역할분담](역할분담.md)에 정리되어 있습니다.

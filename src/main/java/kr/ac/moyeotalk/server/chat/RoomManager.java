package kr.ac.moyeotalk.server.chat;

import kr.ac.moyeotalk.common.Message;
import kr.ac.moyeotalk.server.ClientHandler;

/** 방 생성·참여·채팅을 구현할 자리. 현재는 발신자 검사 외에는 미구현 응답을 보낸다. */
public class RoomManager {
    // B 담당자가 각 메서드에 방 관리 기능을 추가한다.
    // 방 생성 요청의 진입점. 실제 방 저장 기능은 아직 구현하지 않았다.
    public void createRoom(ClientHandler client, Message message) { pending(client); }
    // 방 참여 요청의 진입점. 현재는 미구현 오류를 응답한다.
    public void joinRoom(ClientHandler client, Message message) { pending(client); }
    // 방 나가기 요청의 진입점. 현재는 미구현 오류를 응답한다.
    public void leaveRoom(ClientHandler client, Message message) { pending(client); }
    // 방 삭제 요청의 진입점. 현재는 미구현 오류를 응답한다.
    public void deleteRoom(ClientHandler client, Message message) { pending(client); }
    // 방 ID, 발신자 ID, 채팅 본문을 받고 발신자 일치 여부를 검사한다.
    public void chat(ClientHandler client, Message message) {
        String[] fields = message.fields(3); // 방 ID, 발신자 ID, 공백을 포함한 본문
        // 다른 사용자의 ID로 메시지를 보내는 것을 막는다.
        if (!fields[1].equals(client.getUser().userId())) {
            client.error("SENDER_MISMATCH", "로그인 ID와 발신자 ID가 다릅니다");
            return;
        }
        // 발신자 검사를 통과해도 실제 채팅 전달은 아직 구현하지 않았다.
        pending(client);
    }
    // 메시지 삭제 기능을 연결할 메서드다.
    public void deleteMessage(ClientHandler client, Message message) { pending(client); }
    // 메시지 읽음 표시 기능을 연결할 메서드다.
    public void readMessage(ClientHandler client, Message message) { pending(client); }
    // 연결 종료 시 호출된다. 현재는 방 정보가 없어 아무 작업도 하지 않는다.
    public void disconnect(ClientHandler client) { /* B 담당 구현 예정: 연결이 끊긴 사용자를 모든 참여 방에서 제거 */ }
    // 아직 구현하지 않은 기능이라는 공통 오류 응답을 보낸다.
    private void pending(ClientHandler client) { client.error("NOT_IMPLEMENTED", "B 담당 기능 연결 예정"); }
}

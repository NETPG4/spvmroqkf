package kr.ac.moyeotalk.server.chat;

import kr.ac.moyeotalk.common.Message;
import kr.ac.moyeotalk.server.ClientHandler;

/** B 담당 확장 지점. 방 저장소와 공지/채팅 기능은 이 클래스에서 구현한다. */
public class RoomManager {
    public void createRoom(ClientHandler client, Message message) { pending(client); }
    public void joinRoom(ClientHandler client, Message message) { pending(client); }
    public void leaveRoom(ClientHandler client, Message message) { pending(client); }
    public void deleteRoom(ClientHandler client, Message message) { pending(client); }
    public void chat(ClientHandler client, Message message) {
        String[] fields = message.fields(3); // roomId, senderId, 공백을 포함한 content
        if (!fields[1].equals(client.getUser().userId())) {
            client.error("SENDER_MISMATCH", "로그인 ID와 발신자 ID가 다릅니다");
            return;
        }
        pending(client);
    }
    public void deleteMessage(ClientHandler client, Message message) { pending(client); }
    public void readMessage(ClientHandler client, Message message) { pending(client); }
    public void disconnect(ClientHandler client) { /* B: 모든 참여 방에서 사용자 제거 */ }
    private void pending(ClientHandler client) { client.error("NOT_IMPLEMENTED", "B 담당 기능 연결 예정"); }
}

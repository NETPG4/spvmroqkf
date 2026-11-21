package kr.ac.moyeotalk.server.chat;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import kr.ac.moyeotalk.server.ClientHandler;

/**
 * 채팅방 하나의 정보, 참여자, 최근 메시지를 관리한다.
 * 이 클래스는 잠금을 쓰지 않는다. RoomManager의 synchronized 메서드 안에서만 사용한다.
 */
public class ChatRoom {
    // 메시지를 무한정 쌓으면 메모리가 늘어나므로 방마다 최근 200개만 보관한다.
    private static final int MAX_MESSAGES = 200;

    private final String roomId;    // 방 ID (영문, 숫자, _, -)
    private final String roomName;  // 방 이름 (공백 가능)
    private final Set<ClientHandler> members = new LinkedHashSet<>(); // 참여 사용자 목록
    private final Map<Integer, ChatMessage> messages = new LinkedHashMap<>() {
        // 200개를 넘으면 가장 오래된 메시지부터 지운다.
        @Override protected boolean removeEldestEntry(Map.Entry<Integer, ChatMessage> eldest) {
            return size() > MAX_MESSAGES;
        }
    };
    private int nextMessageId = 1;

    public ChatRoom(String roomId, String roomName) {
        this.roomId = roomId;
        this.roomName = roomName;
    }

    /** 참여자 추가. 이미 들어와 있으면 false. */
    public boolean addMember(ClientHandler client) { return members.add(client); }

    /** 참여자 제거. 원래 없던 사람이면 false. */
    public boolean removeMember(ClientHandler client) { return members.remove(client); }

    public boolean isMember(ClientHandler client) { return members.contains(client); }

    /** 방 전체에 메시지 전달. send는 대기열에 넣기만 하므로 오래 걸리지 않는다. */
    public void broadcastMessage(String line) {
        for (ClientHandler member : members) member.send(line);
    }

    /** 방 전체 중 except만 빼고 전달. 입장·퇴장 알림에 쓴다. */
    public void broadcastExcept(ClientHandler except, String line) {
        for (ClientHandler member : members) {
            if (member != except) member.send(line);
        }
    }

    /** 새 메시지를 만들어 보관하고 돌려준다. */
    public ChatMessage addMessage(String senderId, String content) {
        ChatMessage message = new ChatMessage(nextMessageId++, senderId, content);
        messages.put(message.getMessageId(), message);
        return message;
    }

    /** 번호로 메시지를 찾는다. 없거나 오래돼서 지워졌으면 null. */
    public ChatMessage getMessage(int messageId) { return messages.get(messageId); }

    /** 지금 방에 있는 사람 중 이 메시지를 아직 안 읽은 사람 수. */
    public int countUnread(ChatMessage message) {
        int count = 0;
        for (ClientHandler member : members) {
            if (!message.isReadBy(member.getUser().userId())) count++;
        }
        return count;
    }

    public String getRoomId() { return roomId; }
    public String getRoomName() { return roomName; }
}

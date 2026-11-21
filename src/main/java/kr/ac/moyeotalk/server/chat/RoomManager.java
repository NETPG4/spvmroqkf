package kr.ac.moyeotalk.server.chat;

import java.util.*;
import kr.ac.moyeotalk.common.Message;
import kr.ac.moyeotalk.server.ClientHandler;
import kr.ac.moyeotalk.server.user.User;

/**
 * 채팅방 생성·입장·퇴장, 채팅 전달, 읽음 처리를 맡는다.
 * 여러 클라이언트 스레드가 동시에 부르므로 상태를 바꾸는 메서드는 모두 synchronized다.
 * 방 삭제와 메시지 삭제는 아직 구현하지 않았다.
 */
public class RoomManager {
    // 방 ID -> 채팅방
    private final Map<String, ChatRoom> rooms = new HashMap<>();

    // /CREATE_ROOM roomId roomName : 방을 만들고 만든 사람은 바로 입장한다.
    public synchronized void createRoom(ClientHandler client, Message message) {
        String[] fields = message.fields(2); // 방 ID, 공백을 포함한 방 이름
        checkRoomId(fields[0]);
        if (fields[1].length() > 30) throw new IllegalArgumentException("방 이름은 30자까지입니다");
        if (rooms.containsKey(fields[0])) {
            client.error("DUPLICATE_ROOM", "이미 있는 방입니다");
            return;
        }
        ChatRoom room = new ChatRoom(fields[0], fields[1]);
        room.addMember(client);
        rooms.put(fields[0], room);
        client.send("/ROOM_CREATED " + room.getRoomId() + " " + room.getRoomName());
    }

    // /JOIN_ROOM roomId : 있는 방에 들어가고, 이미 있던 사람들에게 알린다.
    public synchronized void joinRoom(ClientHandler client, Message message) {
        ChatRoom room = findRoom(client, readRoomId(message));
        if (room == null) return;
        if (!room.addMember(client)) {
            client.error("ALREADY_IN_ROOM", "이미 들어간 방입니다");
            return;
        }
        client.send("/JOIN_OK " + room.getRoomId() + " " + room.getRoomName());
        User user = client.getUser();
        room.broadcastExcept(client, "/ROOM_JOINED " + room.getRoomId() + " " + user.userId() + " " + user.nickname());
    }

    // /LEAVE_ROOM roomId : 방에서 나간다. 방은 남아 있다.
    public synchronized void leaveRoom(ClientHandler client, Message message) {
        ChatRoom room = findRoom(client, readRoomId(message));
        if (room == null) return;
        if (!room.removeMember(client)) {
            client.error("NOT_IN_ROOM", "들어가 있지 않은 방입니다");
            return;
        }
        client.send("/LEAVE_OK " + room.getRoomId());
        room.broadcastExcept(client, "/ROOM_LEFT " + room.getRoomId() + " " + client.getUser().userId());
    }

    // 방 삭제는 다음 단계에서 구현한다.
    public void deleteRoom(ClientHandler client, Message message) { pending(client); }

    // /CHAT roomId senderId 본문 : 메시지에 번호를 붙여 방 전체(보낸 사람 포함)에게 보낸다.
    public synchronized void chat(ClientHandler client, Message message) {
        String[] fields = message.fields(3); // 방 ID, 발신자 ID, 공백을 포함한 본문
        // 다른 사용자의 ID로 메시지를 보내는 것을 막는다.
        if (!fields[1].equals(client.getUser().userId())) {
            client.error("SENDER_MISMATCH", "로그인 ID와 발신자 ID가 다릅니다");
            return;
        }
        ChatRoom room = findRoom(client, fields[0]);
        if (room == null) return;
        if (!room.isMember(client)) {
            client.error("NOT_IN_ROOM", "먼저 방에 입장하세요");
            return;
        }
        ChatMessage chat = room.addMessage(fields[1], fields[2]);
        room.broadcastMessage("/CHAT " + room.getRoomId() + " " + chat.getMessageId()
                + " " + chat.getSenderId() + " " + chat.getContent());
    }

    // 메시지 삭제는 다음 단계에서 구현한다.
    public void deleteMessage(ClientHandler client, Message message) { pending(client); }

    // /READ_MESSAGE roomId messageId : 읽음 처리 후 방 전체에 안 읽은 사람 수를 알린다.
    public synchronized void readMessage(ClientHandler client, Message message) {
        String[] fields = message.fields(2); // 방 ID, 메시지 번호
        ChatRoom room = findRoom(client, fields[0]);
        if (room == null) return;
        if (!room.isMember(client)) {
            client.error("NOT_IN_ROOM", "먼저 방에 입장하세요");
            return;
        }
        int messageId;
        try { messageId = Integer.parseInt(fields[1]); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("메시지 번호는 숫자입니다"); }
        ChatMessage chat = room.getMessage(messageId);
        if (chat == null) {
            client.error("MESSAGE_NOT_FOUND", "없는 메시지입니다");
            return;
        }
        // 이미 읽은 메시지를 또 읽음 처리하면 아무것도 보내지 않는다.
        if (chat.markAsRead(client.getUser().userId())) {
            room.broadcastMessage("/MESSAGE_READ " + room.getRoomId() + " " + messageId
                    + " " + client.getUser().userId() + " " + room.countUnread(chat));
        }
    }

    // 연결이 끊기면 들어가 있던 모든 방에서 빼고 남은 사람들에게 알린다.
    // 로그인 전에 끊긴 연결은 getUser()가 null이라 알림 없이 정리만 한다.
    public synchronized void disconnect(ClientHandler client) {
        User user = client.getUser();
        for (ChatRoom room : rooms.values()) {
            if (room.removeMember(client) && user != null) {
                room.broadcastExcept(client, "/ROOM_LEFT " + room.getRoomId() + " " + user.userId());
            }
        }
    }

    // 방 ID 하나만 있는 요청에서 ID를 꺼내 형식을 검사한다.
    private String readRoomId(Message message) {
        String roomId = message.fields(1)[0];
        checkRoomId(roomId);
        return roomId;
    }

    private void checkRoomId(String roomId) {
        if (!roomId.matches("[A-Za-z0-9_-]{1,32}"))
            throw new IllegalArgumentException("방 ID는 영문 숫자 _ - 1~32자입니다");
    }

    // 방을 찾는다. 없으면 오류를 보내고 null을 돌려주므로 호출한 쪽은 null이면 바로 끝낸다.
    private ChatRoom findRoom(ClientHandler client, String roomId) {
        ChatRoom room = rooms.get(roomId);
        if (room == null) client.error("ROOM_NOT_FOUND", "없는 방입니다");
        return room;
    }

    private void pending(ClientHandler client) { client.error("NOT_IMPLEMENTED", "아직 구현하지 않은 기능입니다"); }
}

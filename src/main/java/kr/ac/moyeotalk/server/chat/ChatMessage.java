package kr.ac.moyeotalk.server.chat;

import java.util.HashSet;
import java.util.Set;

/** 채팅 메시지 하나와 그 메시지를 읽은 사람들을 관리한다. */
public class ChatMessage {
    private final int messageId;    // 방 안에서 1부터 늘어나는 메시지 번호
    private final String senderId;  // 보낸 사람 ID
    private final String content;   // 메시지 내용
    private final Set<String> readBy = new HashSet<>(); // 읽은 사용자 ID 목록

    public ChatMessage(int messageId, String senderId, String content) {
        this.messageId = messageId;
        this.senderId = senderId;
        this.content = content;
        // 보낸 사람은 자기 메시지를 이미 읽은 것으로 본다.
        readBy.add(senderId);
    }

    /** 읽음 상태 갱신. 새로 읽은 경우에만 true, 이미 읽은 사람이면 false. */
    public boolean markAsRead(String userId) {
        return readBy.add(userId);
    }

    public boolean isReadBy(String userId) { return readBy.contains(userId); }
    public int getMessageId() { return messageId; }
    public String getSenderId() { return senderId; }
    public String getContent() { return content; }
}

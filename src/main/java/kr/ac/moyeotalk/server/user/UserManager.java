package kr.ac.moyeotalk.server.user;

import kr.ac.moyeotalk.common.Message;
import kr.ac.moyeotalk.server.ClientHandler;
import java.util.*;

/** 로그인 등록과 해제, 사용자 목록 알림을 같은 잠금에서 순서대로 처리한다. */
public final class UserManager {
    private final Map<String, ClientHandler> users = new TreeMap<>();

    public synchronized void login(ClientHandler client, Message message) {
        if (client.isClosed()) return;
        String[] fields = message.fields(2);
        if (client.getUser() != null) { client.error("ALREADY_LOGGED_IN", "이미 로그인했습니다"); return; }
        if (!fields[0].matches("[A-Za-z0-9_-]{1,32}") || !fields[1].matches("[^\\s]{1,32}"))
            throw new IllegalArgumentException("ID는 영문 숫자 _ - 1~32자, 닉네임은 공백 없이 1~32자입니다");
        if (users.containsKey(fields[0])) { client.error("DUPLICATE_USER", "사용 중인 ID입니다"); return; }
        client.setUser(new User(fields[0], fields[1]));
        users.put(fields[0], client);
        client.send("/LOGIN_OK " + fields[0] + " " + fields[1]);
        broadcastUsers();
    }
    public synchronized void listUsers(ClientHandler client, Message message) {
        message.noArguments();
        client.send(userList());
    }
    public void logout(ClientHandler client, Message message) {
        message.noArguments();
        client.send("/LOGOUT_OK");
        client.close();
    }
    public synchronized void disconnect(ClientHandler client) {
        User user = client.getUser();
        if (user != null && users.remove(user.userId(), client)) broadcastUsers();
    }
    public synchronized List<User> getUsers() {
        return users.values().stream().map(ClientHandler::getUser).toList();
    }
    private String userList() {
        StringBuilder line = new StringBuilder("/USER_LIST " + users.size());
        for (ClientHandler client : users.values()) {
            User user = client.getUser();
            line.append(' ').append(user.userId()).append(' ').append(user.nickname());
        }
        return line.toString();
    }
    private void broadcastUsers() {
        String line = userList();
        for (ClientHandler client : users.values()) client.send(line);
    }
}

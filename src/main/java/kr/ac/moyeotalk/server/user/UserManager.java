package kr.ac.moyeotalk.server.user;

import kr.ac.moyeotalk.common.Message;
import kr.ac.moyeotalk.server.ClientHandler;
import java.util.*;

/** 로그인한 사용자를 관리하고, 접속 목록이 바뀌면 모든 사용자에게 알린다. */
public final class UserManager {
    // ID로 연결을 찾으며, TreeMap은 ID 순서로 목록을 정렬한다.
    private final Map<String, ClientHandler> users = new TreeMap<>();

    // synchronized로 동시 로그인을 순서대로 처리하여 ID 중복 등록을 막는다.
    public synchronized void login(ClientHandler client, Message message) {
        // 종료된 연결은 로그인 목록에 새로 등록하지 않는다.
        if (client.isClosed()) return;
        // 로그인 인자를 ID와 닉네임 두 값으로 나눈다.
        String[] fields = message.fields(2);
        // 이미 로그인한 연결이 다른 ID로 다시 로그인하는 것을 막는다.
        if (client.getUser() != null) { client.error("ALREADY_LOGGED_IN", "이미 로그인했습니다"); return; }
        // ID의 허용 문자와 길이, 닉네임의 공백 여부와 길이를 검사한다.
        if (!fields[0].matches("[A-Za-z0-9_-]{1,32}") || !fields[1].matches("[^\\s]{1,32}"))
            throw new IllegalArgumentException("ID는 영문 숫자 _ - 1~32자, 닉네임은 공백 없이 1~32자입니다");
        // 다른 연결에서 사용 중인 ID는 등록할 수 없다.
        if (users.containsKey(fields[0])) { client.error("DUPLICATE_USER", "사용 중인 ID입니다"); return; }
        // 연결 객체에 로그인 정보를 저장한다.
        client.setUser(new User(fields[0], fields[1]));
        // ID를 키로 사용하여 해당 연결을 전체 사용자 목록에 등록한다.
        users.put(fields[0], client);
        // 로그인한 사용자에게 먼저 성공 응답을 보낸다.
        client.send("/LOGIN_OK " + fields[0] + " " + fields[1]);
        // 새 사용자를 포함하여 모든 로그인 사용자에게 갱신된 목록을 알린다.
        broadcastUsers();
    }
    // 요청한 사용자에게만 현재 목록을 보낸다.
    public synchronized void listUsers(ClientHandler client, Message message) {
        // 목록 조회나 로그아웃 명령어 뒤에 불필요한 값이 없는지 검사한다.
        message.noArguments();
        client.send(userList());
    }
    // 로그아웃 응답을 보낸 다음 연결을 종료한다.
    public void logout(ClientHandler client, Message message) {
        // 목록 조회나 로그아웃 명령어 뒤에 불필요한 값이 없는지 검사한다.
        message.noArguments();
        // 응답이 실제 전송된 뒤 연결과 사용자 목록을 정리한다.
        client.sendAndClose("/LOGOUT_OK");
    }
    // 접속이 끊긴 사용자를 제거한 경우에만 새 목록을 알린다.
    public synchronized void disconnect(ClientHandler client) {
        // 해당 연결에 등록된 로그인 정보를 확인한다.
        User user = client.getUser();
        // ID와 연결 객체가 모두 일치할 때만 제거하여 다른 연결을 잘못 지우지 않는다.
        if (user != null && users.remove(user.userId(), client)) broadcastUsers();
    }
    // 연결 객체에서 사용자 정보만 꺼내 목록으로 반환한다.
    public synchronized List<User> getUsers() {
        // 각 연결에서 사용자 정보만 추출하여 수정할 수 없는 목록으로 만든다.
        return users.values().stream().map(ClientHandler::getUser).toList();
    }
    // 전송 형식: /USER_LIST 인원수 ID 닉네임 ID 닉네임 ...
    private String userList() {
        // 응답의 앞부분에 명령어와 현재 로그인 인원수를 넣는다.
        StringBuilder line = new StringBuilder("/USER_LIST " + users.size());
        // ID 순서대로 사용자를 꺼내 응답 뒤에 추가한다.
        for (ClientHandler client : users.values()) {
            // 해당 연결에 등록된 로그인 정보를 확인한다.
            User user = client.getUser();
            // ID와 닉네임 사이에 공백을 넣어 클라이언트가 값을 구분하게 한다.
            line.append(' ').append(user.userId()).append(' ').append(user.nickname());
        }
        // 완성한 사용자 목록을 전송 가능한 문자열로 반환한다.
        return line.toString();
    }
    // 같은 사용자 목록 메시지를 로그인한 모든 연결에 보낸다.
    private void broadcastUsers() {
        // 모든 사용자에게 보낼 동일한 메시지를 한 번만 만든다.
        String line = userList();
        for (ClientHandler client : users.values()) client.send(line);
    }
}

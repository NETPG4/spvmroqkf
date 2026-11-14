package kr.ac.moyeotalk.server.game;

import kr.ac.moyeotalk.common.Message;
import kr.ac.moyeotalk.server.ClientHandler;

/** C 담당 확장 지점. 이모티콘 전송도 C가 구현한다. */
public class GameService {
    public void handle(ClientHandler client, Message message) {
        client.error("NOT_IMPLEMENTED", "C 담당 기능 연결 예정");
    }
}

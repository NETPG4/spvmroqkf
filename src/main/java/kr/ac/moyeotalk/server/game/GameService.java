package kr.ac.moyeotalk.server.game;

import kr.ac.moyeotalk.common.Message;
import kr.ac.moyeotalk.server.ClientHandler;

/** 게임 기록·코인·이모티콘 기능을 구현할 자리. 현재는 미구현 응답을 보낸다. */
public class GameService {
    // C 담당자가 message.command()에 따라 기능을 나누어 구현한다.
    public void handle(ClientHandler client, Message message) {
        // 현재는 기록 저장이나 코인 변경 없이 미구현 상태만 클라이언트에 알린다.
        client.error("NOT_IMPLEMENTED", "C 담당 기능 연결 예정");
    }
}

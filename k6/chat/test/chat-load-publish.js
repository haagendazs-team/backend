/**
 * #2 메시지 전송(PUBLISH) Load Test (haagendazs)
 * - VU: 50 -> 200 (Ramping)
 * - 흐름: CONNECT -> SUBSCRIBE -> 메시지 전송 -> 브로드캐스트 수신
 * - 핵심 지표: stomp_message_roundtrip_ms (메시지 왕복 지연)
 *
 * 실행:
 *   k6 run -e TARGET_HOST=http://<데스크톱IP>:8085 k6/chat/test/chat-load-publish.js
 */
import { sleep } from 'k6';
import exec from 'k6/execution';
import * as stompUtil from '../chat-stomp-util.js';

export const options = {
    stages: [
        { duration: '1m', target: 50 },
        { duration: '2m', target: 100 },
        { duration: '2m', target: 200 },
        { duration: '1m', target: 200 },
        { duration: '1m', target: 0 },
    ],
    thresholds: {
        'stomp_connect_ms': ['p(95)<200'],
        'stomp_message_roundtrip_ms': ['p(95)<500'],   // 메시지 왕복 목표
        'ws_disconnect_rate': ['rate<0.01'],
    },
};

export default function () {
    const memberId = (exec.vu.idInTest % 600) + 1;
    const targetChannelId = (memberId % 50) + 1;

    stompUtil.connectChatServer(
            memberId,
            { tags: { name: 'chat_publish' } },
            null,
            function (socket) {
                // 구독
                stompUtil.subscribeRoom(socket, `sub-${memberId}`, targetChannelId);

                // 0.5초 후부터 메시지 전송 (블로킹 없이)
                socket.setTimeout(function () {
                    stompUtil.sendChatMessage(socket, targetChannelId, { message: `메시지 ${memberId}-1` });
                }, 500);

                socket.setTimeout(function () {
                    stompUtil.sendChatMessage(socket, targetChannelId, { message: `메시지 ${memberId}-2` });
                }, 1000);

                socket.setTimeout(function () {
                    stompUtil.sendChatMessage(socket, targetChannelId, { message: `메시지 ${memberId}-3` });
                }, 1500);

                // 3초 후 종료
                socket.setTimeout(function () {
                    stompUtil.disconnectChatServer(socket);
                }, 3000);
            }
    );
    sleep(1);
}

/**
 * #1 CONNECT Load Test (haagendazs)
 * - VU: 50 -> 200 (Ramping)
 * - 더미 데이터 기준: 회원 1~600, 채널 1~50
 * - 채널 배정 규칙: channelId = (memberId % 50) + 1  (dummy-data.sql과 동일)
 *
 * 실행:
 *   k6 run -e TARGET_HOST=http://<데스크톱IP>:8085 k6/tests/chat/chat-load-connect.js
 *
 * 1 VU 스모크 테스트:
 *   k6 run -e TARGET_HOST=http://<데스크톱IP>:8085 --vus 1 --duration 10s k6/tests/chat/chat-load-connect.js
 */
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import * as chatAPI from '../chat-service.js';
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
        'ws_disconnect_rate': ['rate<0.01'],
        'http_req_duration': ['p(95)<200'],
        'http_req_failed': ['rate<0.001'],
    },
};

export default function () {
    // 더미 데이터: 회원 1~600
    const memberId = (exec.vu.idInTest % 600) + 1;
    const targetChannelId = (memberId % 50) + 1;

    // 1. 채팅 내역 조회 (커서 없이 최신 30개)
    const historyRes = chatAPI.getChatHistory(memberId, targetChannelId);
    check(historyRes, {
        'GET /channels/{channelId}/messages is 200': (r) => r.status === 200,
    });
    sleep(0.5);

    // 2. WebSocket CONNECT
    stompUtil.connectChatServer(
            memberId,
            { tags: { name: 'chat_connect' } },
            null,
            function (socket) {
                sleep(1);
                stompUtil.disconnectChatServer(socket);
            }
    );
    sleep(1);
}

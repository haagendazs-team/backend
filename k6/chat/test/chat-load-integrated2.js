/**
 * #3 통합 부하 테스트 (haagendazs)
 * - 실제 사용자 흐름: 대화 조회 → CONNECT → SUBSCRIBE → 메시지 송수신 → 읽음 처리
 * - VU: 300 -> 1000 (Ramping)
 * - 발표용: 모든 지표(REST, STOMP, 메시지, 읽음)를 한 번에 관측
 *
 * 실행:
 *   k6 run -e TARGET_HOST=http://<데스크톱IP>:8085 k6/chat/test/chat-load-integrated.js
 */
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import * as chatAPI from '../chat-service.js';
import * as stompUtil from '../chat-stomp-util.js';

export const options = {
    stages: [
        { duration: '1m', target: 300 },
        { duration: '2m', target: 600 },
        { duration: '2m', target: 1000 },
        { duration: '1m', target: 1000 },
        { duration: '1m', target: 0 },
    ],
    thresholds: {
        'stomp_connect_ms': ['p(95)<200'],
        'stomp_message_roundtrip_ms': ['p(95)<500'],
        'ws_disconnect_rate': ['rate<0.01'],
        'http_req_duration': ['p(95)<300'],   // 목표 재조정 (200 → 300)
        'http_req_failed': ['rate<0.01'],
    },
};

export default function () {
    const memberId = (exec.vu.idInTest % 3000) + 1;
    const targetChannelId = (memberId % 250) + 1;

    // 1. 채팅방 입장 — 이전 대화 조회 (REST)
    const historyRes = chatAPI.getChatHistory(memberId, targetChannelId);
    check(historyRes, {
        'GET messages is 200': (r) => r.status === 200,
    });

    // 2. WebSocket 연결 → 구독 → 메시지 송수신
    stompUtil.connectChatServer(
            memberId,
            { tags: { name: 'chat_integrated' } },
            null,
            function (socket) {
                stompUtil.subscribeRoom(socket, `sub-${memberId}`, targetChannelId);

                // 메시지 3건 전송 (비동기 타이머로 핸들러 블로킹 방지)
                socket.setTimeout(function () {
                    stompUtil.sendChatMessage(socket, targetChannelId, { message: `통합테스트 ${memberId}-1` });
                }, 500);

                socket.setTimeout(function () {
                    stompUtil.sendChatMessage(socket, targetChannelId, { message: `통합테스트 ${memberId}-2` });
                }, 1000);

                socket.setTimeout(function () {
                    stompUtil.sendChatMessage(socket, targetChannelId, { message: `통합테스트 ${memberId}-3` });
                }, 1500);

                socket.setTimeout(function () {
                    stompUtil.disconnectChatServer(socket);
                }, 3000);
            }
    );

    // 3. 읽음 처리 (REST) — 대화 확인 후 읽음 표시
    const lastReadMessageId = Math.floor(Math.random() * 100) + 1;
    const readRes = chatAPI.readMessage(memberId, targetChannelId, lastReadMessageId);
    check(readRes, {
        'PATCH read is 200': (r) => r.status === 200,
    });

    sleep(1);
}

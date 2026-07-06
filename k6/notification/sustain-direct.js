// sustain-direct.js — Gateway/JWT 없이 notification 직접 SSE 부하 테스트
// X-Member-Id 헤더로 notification:8081 에 직접 접속
//
// 흐름:
//   SSE 연결 점진적 증가 → body 읽어 ping 감지 → 서버에 ack POST → Grafana 반영
//   MAX_VUS 미설정 시 포트/메모리 한계까지 무제한 증가
import { sleep } from 'k6';
import http from 'k6/http';
import { Counter } from 'k6/metrics';

const NOTIFICATION_URL = __ENV.NOTIFICATION_URL || 'http://localhost:8081';
const MAX_VUS          = parseInt(__ENV.MAX_VUS   || '0', 10);   // 0 = 무제한
const START_VUS        = parseInt(__ENV.START_VUS || '6000',  10);
const SEED_OFFSET      = parseInt(__ENV.K6_SEED_OFFSET || '10000', 10);
const STEP             = 1000;
const UNLIMITED_CAP    = 30000; // ephemeral port 여유분 기준 상한 (portrange 16384~65535 = 49151)

const targetMax = MAX_VUS > 0 ? MAX_VUS : UNLIMITED_CAP;

function buildStepStages(start, max, step) {
    const stages = [];
    for (let t = start + step; t <= max; t += step) {
        stages.push({ duration: '10s', target: t });  // 램프업
        stages.push({ duration: '20s', target: t });  // heartbeat 확인
    }
    return stages;
}

const sseCompleted       = new Counter('sse_session_completed');
const sseDisconnected    = new Counter('sse_server_disconnect');
const sseConnectAttempts = new Counter('sse_connect_attempts');
const sseConnectSuccess  = new Counter('sse_connect_success');
const ssePoolExhausted   = new Counter('sse_pool_exhausted');
const sseReplayed        = new Counter('sse_replayed');
const ssePingReceived    = new Counter('sse_ping_received');
const ssePingAckSent     = new Counter('sse_ping_ack_sent');
const ssePingAckFailed   = new Counter('sse_ping_ack_failed');

export const options = {
    scenarios: {
        sustain_ramp: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: START_VUS },
                ...buildStepStages(START_VUS, targetMax, STEP),
                { duration: '2m',  target: targetMax },
                { duration: '30s', target: 0 },
            ],
            gracefulRampDown: '30s',
        },
    },
    thresholds: {
        sse_server_disconnect: ['count<10'],
        sse_pool_exhausted:    ['count<10'],
    },
};

export default function () {
    const memberId = SEED_OFFSET + (__VU % targetMax) + 1;

    let lastEventId = null;

    while (true) {
        sseConnectAttempts.add(1);

        const headers = {
            Accept:        'text/event-stream',
            'X-Member-Id': String(memberId),
        };
        if (lastEventId !== null) {
            headers['Last-Event-ID'] = String(lastEventId);
            sseReplayed.add(1);
        }

        // body를 읽어야 ping 감지 가능 — responseType 기본값(text) 사용
        const res = http.get(
            `${NOTIFICATION_URL}/api/notifications/stream`,
            {
                headers,
                timeout: '1850s',
            }
        );

        const isTimeout    = res.status === 0 && (res.error_code === 1050 || res.error_code === 1000);
        const isSuccess    = res.status === 200;
        const isExhausted  = res.status === 503 || res.error_code === 1212;

        if (isTimeout || isSuccess) {
            sseConnectSuccess.add(1);

            const body      = res.body || '';
            const pingCount = (body.match(/event: ping\r?$/gm) || []).length;

            if (pingCount > 0) {
                ssePingReceived.add(pingCount);
                sendAck(memberId, pingCount);
            }

            if (isTimeout) {
                sseCompleted.add(1);
            }
            lastEventId = null;
            sleep(1);
            continue;
        }

        if (isExhausted) {
            ssePoolExhausted.add(1);
        } else {
            if (__ITER < 3) {
                console.log(`SSE disconnect status=${res.status} error_code=${res.error_code} error=${res.error}`);
            }
            sseDisconnected.add(1);
        }
        lastEventId = null;
        sleep(3);
    }
}

function sendAck(memberId, pingCount) {
    const now    = new Date().toISOString().replace('Z', '');
    const ackRes = http.post(
        `${NOTIFICATION_URL}/api/notifications/stream/ack`,
        JSON.stringify({ status: '성공', pingReceivedAt: now }),
        {
            headers: {
                'Content-Type': 'application/json',
                'X-Member-Id': String(memberId),
            },
            timeout: '5s',
        }
    );

    if (ackRes.status === 204) {
        ssePingAckSent.add(pingCount);
    } else {
        ssePingAckFailed.add(1);
    }
}

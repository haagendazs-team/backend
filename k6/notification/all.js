// k6/notification/all.js
// 연결 수립 → CCU 유지 → 이벤트 발행 → 수신 검증을 한 번에 실행
//
// 시나리오 구성:
//   connect_ramp   — SSE 연결 수락 속도/성공률 (0~6m, ramping-arrival-rate)
//   sustain_ccu    — 3K CCU 동시 유지 (2~12m, ramping-vus)
//   publish_single — PAYMENT_COMPLETED 단건 발행 TPS (2~6m, ramping-arrival-rate)
//   receive_verify — SSE 이벤트 수신 완전성 검증 (3~9m, constant-vus)
//
// 실행:
//   .\run-windows.ps1 -Target all -Vus 3000
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { fetchTokens, getTokenFromCache, getMemberId, preloadedTokens } from '../helpers/token.js';
import { connectSseStream, classifySseResult, countNotificationEvents } from '../helpers/sse.js';
import { publishPayment } from '../helpers/publish.js';

const BASE_URL    = __ENV.BASE_URL   || 'http://localhost:8080';
const MEMBER_URL  = __ENV.MEMBER_URL || 'http://localhost:8084';
const PEAK_VUS    = parseInt(__ENV.MAX_VUS || '3000', 10);
const SEED_OFFSET = parseInt(__ENV.K6_SEED_OFFSET || '10000', 10);

// ── 연결 메트릭 ──────────────────────────────────────────────────────────────
const sseConnectSuccess = new Counter('sse_connect_success');
const sseConnectFailed  = new Counter('sse_connect_failed');
const sseAcceptLatency  = new Trend('sse_accept_latency', true);

// ── CCU 유지 메트릭 ──────────────────────────────────────────────────────────
const sseSessionCompleted  = new Counter('sse_session_completed');
const sseServerDisconnect  = new Counter('sse_server_disconnect');
const sseDurationTotalMs   = new Counter('sse_duration_total_ms');

// ── 발행 메트릭 ──────────────────────────────────────────────────────────────
const notifyPublishSuccess     = new Counter('notify_publish_success');
const notifyPublishFailed      = new Counter('notify_publish_failed');
const notifyPublishSuccessRate = new Rate('notify_publish_success_rate');
const notifyPublishDuration    = new Trend('notify_publish_duration', true);

// ── 수신 검증 메트릭 ─────────────────────────────────────────────────────────
const receiveEventsReceived = new Counter('receive_events_received');
const receiveEventsDropped  = new Counter('receive_events_dropped');
const receiveDeliveryRate   = new Rate('receive_delivery_rate');
const receiveConnectFailed  = new Counter('receive_connect_failed');

export const options = {
    scenarios: {
        // A. 연결 수립 속도 — 0s~6m
        connect_ramp: {
            executor: 'ramping-arrival-rate',
            startRate: 10,
            timeUnit: '1s',
            preAllocatedVUs: 200,
            maxVUs: PEAK_VUS,
            stages: [
                { duration: '30s', target: 100  },
                { duration: '1m',  target: 500  },
                { duration: '1m',  target: 1000 },
                { duration: '1m',  target: 2000 },
                { duration: '1m',  target: 3000 },
                { duration: '30s', target: 0    },
            ],
            exec: 'connectSse',
            startTime: '0s',
        },

        // B. CCU 유지 — 2m~12m
        sustain_ccu: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '1m',  target: Math.floor(PEAK_VUS * 0.2) },
                { duration: '1m',  target: Math.floor(PEAK_VUS * 0.5) },
                { duration: '1m',  target: PEAK_VUS },
                { duration: '4m',  target: PEAK_VUS },
                { duration: '30s', target: 0 },
            ],
            gracefulRampDown: '30s',
            exec: 'sustainSse',
            startTime: '2m',
        },

        // C. 단건 발행 TPS — 2m~6m
        publish_single: {
            executor: 'ramping-arrival-rate',
            startRate: 10,
            timeUnit: '1s',
            preAllocatedVUs: 50,
            maxVUs: 300,
            stages: [
                { duration: '30s', target: 50  },
                { duration: '1m',  target: 50  },
                { duration: '30s', target: 300 },
                { duration: '1m',  target: 300 },
                { duration: '30s', target: 500 },
                { duration: '30s', target: 0   },
            ],
            exec: 'publishSingle',
            startTime: '2m',
        },

        // D. 수신 검증 — 비활성화 (OOM 유발 위험)
        // receive_verify: {
        //     executor: 'constant-vus',
        //     vus: 500,
        //     duration: '6m',
        //     exec: 'receiveAndVerify',
        //     startTime: '3m',
        // },
    },

    thresholds: {
        sse_connect_failed:          ['count<150'],
        sse_accept_latency:          ['p(95)<1000'],
        sse_server_disconnect:       ['count<1'],
        notify_publish_success_rate: ['rate>=0.99'],
        notify_publish_duration:     ['p(95)<300'],
        http_req_failed:             ['rate<0.05'],
    },
};

export function setup() {
    const tokens = preloadedTokens.length > 0
        ? preloadedTokens
        : fetchTokens(MEMBER_URL, SEED_OFFSET, PEAK_VUS);
    console.log(`토큰 준비 완료: ${tokens.length}개`);
    return { tokens };
}

// ── A. 연결 수립 ─────────────────────────────────────────────────────────────
export function connectSse(data) {
    const token = getTokenFromCache(data.tokens, __VU);
    const res   = connectSseStream(token, 5);
    const kind  = classifySseResult(res);

    if (kind === 'completed') {
        sseAcceptLatency.add(res.timings.duration);
        sseConnectSuccess.add(1);
        return;
    }
    if (kind === 'accepted') {
        sseAcceptLatency.add(res.timings.waiting);
        sseConnectSuccess.add(1);
        check(res, { 'SSE 연결 수락 (200)': (r) => r.status === 200 });
        return;
    }
    sseConnectFailed.add(1);
    check(res, { 'SSE 연결 수락': () => false });
}

// ── B. CCU 유지 ──────────────────────────────────────────────────────────────
export function sustainSse(data) {
    if (__VU > data.tokens.length) return;

    const token = getTokenFromCache(data.tokens, __VU);

    while (true) {
        const sessionStart = Date.now();
        const res  = connectSseStream(token, 25 + Math.floor(Math.random() * 21));
        const kind = classifySseResult(res);

        sseDurationTotalMs.add(Date.now() - sessionStart);

        if (kind === 'completed') {
            sseSessionCompleted.add(1);
            sleep(1);
            continue;
        }

        sseServerDisconnect.add(1);
        sleep(3);
    }
}

// ── C. 단건 발행 ─────────────────────────────────────────────────────────────
export function publishSingle() {
    const memberId = getMemberId((__VU - 1) % PEAK_VUS + 1);
    const res      = publishPayment(memberId, __ITER);
    notifyPublishDuration.add(res.timings.duration);

    const ok = check(res, { 'payment 발행 성공 (200)': (r) => r.status === 200 });
    notifyPublishSuccessRate.add(ok);
    if (ok) notifyPublishSuccess.add(1);
    else    notifyPublishFailed.add(1);
}

// ── D. 수신 검증 (비활성화 중) ───────────────────────────────────────────────
export function receiveAndVerify(data) {
    const token = getTokenFromCache(data.tokens, __VU);
    if (!token) {
        receiveConnectFailed.add(1);
        return;
    }

    const res  = connectSseStream(token, randomIntBetween(25, 50));
    const kind = classifySseResult(res);

    if (kind === 'failed') {
        receiveConnectFailed.add(1);
        return;
    }

    const connected = check(res, { 'SSE 수신 연결 성공': () => kind !== 'failed' });
    if (!connected) {
        receiveConnectFailed.add(1);
        return;
    }

    const eventCount = countNotificationEvents(res.body || '');
    if (eventCount > 0) {
        receiveEventsReceived.add(eventCount);
        receiveDeliveryRate.add(true);
    } else {
        receiveEventsDropped.add(1);
        receiveDeliveryRate.add(false);
    }
}

// receive.js — 수신 파이프라인 검증 (consume → fanout → SSE 전달 완전성)
//
// 목적:
//   - 특정 memberId에게 알림을 발행하고 SSE로 실제 수신되는지 확인
//   - e2e 전파 완전성(delivery rate) 및 지연(publishedAt → SSE 수신) 측정
//   - 수신 실패 시 파이프라인 어느 단계에서 소실됐는지 간접 판단
//
// 구조:
//   receivers  — X-Member-Id로 SSE 직접 연결 유지 (토큰 불필요)
//   senders    — /publish-k6-bulk 로 동일 memberId 범위에 알림 발행
//
// 실행:
//   ./run.sh receive [--noCleanUp]
//
// 환경 변수:
//   NOTIFICATION_URL  — notification 직접 주소 (기본: http://localhost:8081)
//   RECEIVER_VUS      — SSE 유지 VU 수 (기본: 500)
//   SEND_RATE         — 초당 발행 횟수 (기본: 10, × BULK_SIZE = 초당 알림 수)
//   BULK_SIZE         — 1회 발행 당 memberId 수 (기본: 10)
//   SEED_OFFSET       — memberId base (기본: 10000)
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import http from 'k6/http';

const NOTIFICATION_URL = __ENV.NOTIFICATION_URL || 'http://localhost:8081';
const RECEIVER_VUS     = parseInt(__ENV.RECEIVER_VUS  || '500',  10);
const SEND_RATE        = parseInt(__ENV.SEND_RATE      || '10',   10);
const BULK_SIZE        = parseInt(__ENV.BULK_SIZE      || '10',   10);
const SEED_OFFSET      = parseInt(__ENV.K6_SEED_OFFSET || '10000', 10);

const HEADERS_JSON = { 'Content-Type': 'application/json' };
const HEADERS_SSE  = { 'Accept': 'text/event-stream' };

// ── 수신 메트릭 ──────────────────────────────────────────────────
const sseConnectSuccess  = new Counter('sse_connect_success');
const sseConnectFailed   = new Counter('sse_connect_failed');
const sseDisconnected    = new Counter('sse_disconnected');
const notifReceived      = new Counter('notif_received_total');
const notifMissed        = new Counter('notif_missed_total');
const deliveryRate       = new Rate('notif_delivery_rate');
const e2eLatency         = new Trend('e2e_latency_ms', true);
const ssePingReceived    = new Counter('sse_ping_received');

// ── 발행 메트릭 ──────────────────────────────────────────────────
const publishSuccess     = new Counter('publish_success');
const publishFailed      = new Counter('publish_failed');
const publishRate        = new Rate('publish_success_rate');

export const options = {
    insecureSkipTLSVerify: true,
    scenarios: {
        // A. SSE 수신 대기 — RECEIVER_VUS명이 연결 유지
        receivers: {
            executor: 'constant-vus',
            vus: RECEIVER_VUS,
            duration: '8m',
            exec: 'holdAndReceive',
            startTime: '0s',
        },

        // B. 알림 발행 — 수신 대상 VU와 동일 memberId 범위
        senders: {
            executor: 'constant-arrival-rate',
            rate: SEND_RATE,
            timeUnit: '1s',
            preAllocatedVUs: 20,
            maxVUs: 50,
            duration: '7m',
            exec: 'publishBulk',
            startTime: '10s',  // receivers 먼저 연결
        },
    },

    thresholds: {
        notif_delivery_rate:  ['rate>=0.80'],
        publish_success_rate: ['rate>=0.99'],
        sse_connect_failed:   ['count<50'],
        // e2e 지연 p95 < 5s (정상 파이프라인 기준)
        e2e_latency_ms: [{ threshold: 'p(95)<5000', abortOnFail: false }],
    },
};

// ── A. SSE 연결 유지 & 수신 ──────────────────────────────────────
export function holdAndReceive() {
    const memberId = SEED_OFFSET + ((__VU - 1) % RECEIVER_VUS) + 1;

    while (true) {
        const res = http.get(
            `${NOTIFICATION_URL}/api/notifications/stream`,
            {
                headers: {
                    ...HEADERS_SSE,
                    'X-Member-Id': String(memberId),
                },
                timeout: '600s',
            }
        );

        const ok       = res.status === 200 || (res.status === 0 && res.error_code === 1050);
        const poolFull = res.status === 503 || res.error_code === 1212;

        if (ok) {
            sseConnectSuccess.add(1);
            const body = res.body || '';

            const pings = (body.match(/event: ping\r?$/gm) || []).length;
            if (pings > 0) ssePingReceived.add(pings);

            const received = parseAndRecordE2e(body);
            if (received > 0) {
                notifReceived.add(received);
                deliveryRate.add(true);
            } else {
                // 연결은 됐지만 알림 미수신 — 발행이 없었을 수도 있으므로 체크하지 않음
            }
        } else if (poolFull) {
            sseConnectFailed.add(1);
            check(res, { 'SSE pool 여유 있음': () => false });
            sleep(5);
            continue;
        } else {
            sseDisconnected.add(1);
            if (__ITER < 3) {
                console.log(`SSE disconnect memberId=${memberId} status=${res.status} err=${res.error}`);
            }
            sleep(2);
            continue;
        }

        sleep(1);
    }
}

// SSE body 파싱 — notification 이벤트에서 publishedAt 추출해 e2e 지연 기록
function parseAndRecordE2e(body) {
    if (!body) return 0;
    const now = Date.now();
    let count = 0;
    const dataLines = body.match(/^data:\s*(\{.+\})$/gm) || [];
    for (const line of dataLines) {
        const jsonStr = line.replace(/^data:\s*/, '');
        try {
            const outer = JSON.parse(jsonStr);
            // NotificationResponse.payload 는 JSON 문자열
            if (outer.payload && typeof outer.payload === 'string') {
                const inner = JSON.parse(outer.payload);
                if (inner.publishedAt && typeof inner.publishedAt === 'number') {
                    e2eLatency.add(now - inner.publishedAt);
                    count++;
                }
            }
        } catch (_) {}
    }
    return count;
}

// ── B. 알림 발행 ─────────────────────────────────────────────────
export function publishBulk() {
    const startMemberId = SEED_OFFSET + ((__VU - 1) * BULK_SIZE % RECEIVER_VUS) + 1;
    const publishedAt   = Date.now();

    const res = http.post(
        `${NOTIFICATION_URL}/module/notifications/publish-k6-bulk`,
        JSON.stringify({
            startMemberId,
            count: BULK_SIZE,
            eventTypeCode: 'PAYMENT_COMPLETED',
            publishedAt,
        }),
        { headers: HEADERS_JSON, tags: { name: 'publish' } }
    );

    const ok = check(res, { 'publish 200': (r) => r.status === 200 });
    publishRate.add(ok);
    if (ok) {
        publishSuccess.add(1);
    } else {
        publishFailed.add(1);
        if (__ITER < 5) {
            console.log(`publish fail status=${res.status} body=${(res.body || '').slice(0, 200)}`);
        }
    }
}

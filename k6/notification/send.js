// send.js — 6,000 SSE 상시 유지 + 무제한 TPS 램프업 부하 테스트
//
// 시나리오:
//   sseHolder   — 6,000 VU가 SSE 연결을 상시 유지. 서버 SSE timeout(2m) 후 닫히면 body 파싱해 e2e 측정.
//   bulkSender  — ramping-arrival-rate로 TPS를 무제한 상승. 메모리·지연 초과 시 abort.
//
// 종료 조건 (abortOnFail):
//   - e2e 지연 p(99) > 10,000ms
//   - JVM 힙 사용률 > 90% (heap-guard.sh 가 서버 프로세스를 종료하므로, 여기서는 TPS 지연으로 간접 감지)
//
// 실행:
//   ./run.sh send [--noCleanUp]
//
// 환경 변수:
//   NOTIFICATION_URL  — notification 직접 주소 (기본: http://localhost:8081)
//   SSE_VUS           — SSE 유지 VU 수 (기본: 6000)
//   BULK_SIZE         — 1회 발행 당 memberId 수 (기본: 50)
//   SEED_OFFSET       — memberId base (기본: 10000)
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import http from 'k6/http';

const NOTIFICATION_URL = __ENV.NOTIFICATION_URL || 'http://localhost:8081';
const SSE_VUS          = parseInt(__ENV.SSE_VUS        || '6000', 10);
const BULK_SIZE        = parseInt(__ENV.BULK_SIZE      || '50',   10);
const SEED_OFFSET      = parseInt(__ENV.K6_SEED_OFFSET || '10000', 10);

const HEADERS_JSON = { 'Content-Type': 'application/json' };
const HEADERS_SSE  = { 'Accept': 'text/event-stream' };

// ── 발행 메트릭 ──────────────────────────────────────────────────
const bulkPublishSuccess  = new Counter('bulk_publish_success');
const bulkPublishFailed   = new Counter('bulk_publish_failed');
const bulkPublishRate     = new Rate('bulk_publish_success_rate');
const bulkPublishDuration = new Trend('bulk_publish_duration', true);
const bulkEnqueuedTotal   = new Counter('bulk_publish_enqueued_total');

// ── SSE / e2e 메트릭 ─────────────────────────────────────────────
const sseConnectSuccess  = new Counter('sse_connect_success');
const sseConnectFailed   = new Counter('sse_connect_failed');
const sseDisconnected    = new Counter('sse_disconnected');
const ssePingReceived    = new Counter('sse_ping_received');
const e2eLatency         = new Trend('e2e_latency_ms', true);   // 발행→수신 ms
const e2eReceived        = new Counter('e2e_received_total');

export const options = {
    insecureSkipTLSVerify: true,
    scenarios: {
        // A. SSE 6,000 VU 상시 유지
        sseHolder: {
            executor: 'constant-vus',
            vus: SSE_VUS,
            duration: '10m',
            exec: 'holdSse',
            startTime: '0s',
        },

        // B. Bulk 발행 TPS 무제한 램프업
        bulkSender: {
            executor: 'ramping-arrival-rate',
            startRate: 10,
            timeUnit: '1s',
            preAllocatedVUs: 50,
            maxVUs: 4000,
            startTime: '10s',  // SSE 연결이 먼저 올라온 뒤 시작
            stages: [
                { duration: '30s', target: 20  },  // 1k  알림/s  (BULK_SIZE=50 × 20)
                { duration: '1m',  target: 60  },  // 3k  알림/s
                { duration: '1m',  target: 120 },  // 6k  알림/s
                { duration: '1m',  target: 200 },  // 10k 알림/s
                { duration: '1m',  target: 400 },  // 20k 알림/s
                { duration: '1m',  target: 800 },  // 40k 알림/s — 한계 탐색
                { duration: '30s', target: 0   },
            ],
            exec: 'sendBulk',
        },
    },

    thresholds: {
        // e2e 지연 p99 > 10s 이면 즉시 abort
        e2e_latency_ms: [
            { threshold: 'p(99)<10000', abortOnFail: true, delayAbortEval: '30s' },
        ],
        bulk_publish_success_rate: ['rate>=0.95'],
        http_req_failed:           ['rate<0.10'],
    },
};

// ── A. SSE 유지 VU ───────────────────────────────────────────────
// 서버 SSE timeout(2m)마다 연결이 닫히고 body를 파싱해 e2e 지연 계산 후 재연결.
export function holdSse() {
    const memberId = SEED_OFFSET + ((__VU - 1) % SSE_VUS) + 1;

    while (true) {
        const res = http.get(
            `${NOTIFICATION_URL}/api/notifications/stream`,
            {
                headers: {
                    ...HEADERS_SSE,
                    'X-Member-Id': String(memberId),
                },
                timeout: '150s',
            }
        );

        const ok       = res.status === 200 || (res.status === 0 && res.error_code === 1050);
        const poolFull = res.status === 503 || res.error_code === 1212;

        if (ok) {
            sseConnectSuccess.add(1);
            parseE2eLatency(res.body || '');

            const pingCount = (res.body || '').match(/event: ping\r?$/gm);
            if (pingCount) ssePingReceived.add(pingCount.length);
        } else if (poolFull) {
            sseConnectFailed.add(1);
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

// SSE body에서 publishedAt 필드를 읽어 e2e 지연(ms)을 Trend에 기록.
// NotificationResponse 구조: { id, eventTypeCode, payload: "{...publishedAt...}", ... }
// publishedAt은 payload 문자열 안에 JSON으로 인코딩되어 있다.
function parseE2eLatency(body) {
    if (!body) return;
    const now = Date.now();
    const dataLines = body.match(/^data:\s*(\{.+\})$/gm) || [];
    for (const line of dataLines) {
        const jsonStr = line.replace(/^data:\s*/, '');
        try {
            const outer = JSON.parse(jsonStr);
            let publishedAt = null;
            if (outer.payload && typeof outer.payload === 'string') {
                const inner = JSON.parse(outer.payload);
                publishedAt = inner.publishedAt;
            } else if (typeof outer.publishedAt === 'number') {
                publishedAt = outer.publishedAt;
            }
            if (publishedAt && typeof publishedAt === 'number') {
                e2eLatency.add(now - publishedAt);
                e2eReceived.add(1);
            }
        } catch (_) {}
    }
}

// ── B. Bulk 발행 VU ──────────────────────────────────────────────
export function sendBulk() {
    const startMemberId = SEED_OFFSET + ((__VU - 1) * BULK_SIZE % SSE_VUS) + 1;
    const publishedAt   = Date.now();

    const body = JSON.stringify({
        startMemberId,
        count: BULK_SIZE,
        eventTypeCode: 'PAYMENT_COMPLETED',
        publishedAt,
    });

    const res = http.post(
        `${NOTIFICATION_URL}/module/notifications/publish-k6-bulk`,
        body,
        { headers: HEADERS_JSON, tags: { name: 'bulk_publish' } }
    );

    bulkPublishDuration.add(res.timings.duration);
    const ok = check(res, { 'bulk publish 200': (r) => r.status === 200 });
    bulkPublishRate.add(ok);

    if (ok) {
        bulkPublishSuccess.add(1);
        bulkEnqueuedTotal.add(BULK_SIZE);
    } else {
        bulkPublishFailed.add(1);
        if (__ITER < 5) {
            console.log(`bulk publish fail status=${res.status} body=${(res.body || '').slice(0, 200)}`);
        }
    }
}

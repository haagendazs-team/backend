// send.js — 24,000 SSE 상시 유지 + 부서 단위 브로드캐스트 최대 N 탐색
//
// 시나리오:
//   sseHolder  — 30s 안에 24,000 SSE 연결 완료 후 상시 유지.
//   bulkSender — 부서 단위(500→1K→3K→5K명) 단계별 발송. 끝까지 돌려 무너지는 N을 찾는다.
//
// 핵심 설계:
//   - http_req_failed 태그 분리: SSE 연결 실패가 발송 SLA 지표를 오염시키지 않도록 name 태그로 격리
//   - 단계별 bulk_size 태그: "3K까진 99%, 5K에서 깨짐"을 단계별로 판정 가능
//   - abortOnFail 제거: 중간 단계에서 중단되면 이후 데이터가 사라져 최대 N을 찾을 수 없음
//   - e2e 지연: http.get은 연결 종료 후 body를 읽으므로 SSE timeout을 짧게 설정해 오차 최소화
//
// 실행:
//   ./run.sh send [--noCleanUp]
//
// 환경 변수:
//   NOTIFICATION_URL  — 기본: http://localhost:8081
//   SSE_VUS           — SSE 유지 VU 수 (기본: 24000)
//   SEED_OFFSET       — memberId base (기본: 10000)

import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import http from 'k6/http';

const NOTIFICATION_URL = __ENV.NOTIFICATION_URL  || 'http://localhost:8081';
const SSE_VUS          = parseInt(__ENV.SSE_VUS        || '24000', 10);
const SEED_OFFSET      = parseInt(__ENV.K6_SEED_OFFSET || '10000', 10);

const HEADERS_JSON = { 'Content-Type': 'application/json' };
const HEADERS_SSE  = { 'Accept': 'text/event-stream' };

// ── 발행 메트릭 ──────────────────────────────────────────────────────
const bulkPublishSuccess  = new Counter('bulk_publish_success');
const bulkPublishFailed   = new Counter('bulk_publish_failed');
const bulkPublishRate     = new Rate('bulk_publish_success_rate');
const bulkPublishDuration = new Trend('bulk_publish_duration', true);
const bulkEnqueuedTotal   = new Counter('bulk_publish_enqueued_total');

// ── SSE / e2e 메트릭 ─────────────────────────────────────────────────
const sseConnectSuccess = new Counter('sse_connect_success');
const sseConnectFailed  = new Counter('sse_connect_failed');
const sseDisconnected   = new Counter('sse_disconnected');
const ssePingReceived   = new Counter('sse_ping_received');
const e2eLatency        = new Trend('e2e_latency_ms', true);
const e2eReceived       = new Counter('e2e_received_total');

// ── 부서 단위 발송 단계 ───────────────────────────────────────────────
// VU 10개 고정, bulkSize만 단계별로 확대 → dropped_iterations 방지
// 각 단계 sleep은 stageSleepSec()으로 서버 부하 조절
const STAGES = [
    { duration: '1m',  bulkSize: 500  },
    { duration: '2m',  bulkSize: 1000 },
    { duration: '2m',  bulkSize: 3000 },
    { duration: '2m',  bulkSize: 5000 },
];
const SENDER_VUS = 10;

const TOTAL_SEND_SEC = STAGES.reduce((s, st) => s + parseDurationSec(st.duration), 0);
const TOTAL_SEC      = 40 + TOTAL_SEND_SEC + 30; // 워밍업 40s + 발송 + 쿨다운 30s

export const options = {
    insecureSkipTLSVerify: true,
    scenarios: {
        // A. 24,000 SSE 연결 — 30s 안에 풀 연결 후 상시 유지
        sseHolder: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s',              target: SSE_VUS },
                { duration: `${TOTAL_SEC - 30}s`, target: SSE_VUS },
            ],
            exec: 'holdSse',
            startTime: '0s',
        },

        // B. 부서 단위 발송 — SSE 24K 완료(30s) + 안정화(10s) 후 시작
        bulkSender: {
            executor: 'constant-vus',
            vus: SENDER_VUS,
            duration: `${TOTAL_SEND_SEC + 30}s`,
            exec: 'sendBulk',
            startTime: '40s',
        },
    },

    thresholds: {
        // ① SSE 연결 요청을 제외하고 발송 요청만 판정
        'http_req_failed{name:bulk_publish}': ['rate<0.01'],

        // ② 단계별 성공률 — abortOnFail 없이 끝까지 돌려 최대 N을 찾는다
        'bulk_publish_success_rate{bulk_size:500}':  ['rate>=0.99'],
        'bulk_publish_success_rate{bulk_size:1000}': ['rate>=0.99'],
        'bulk_publish_success_rate{bulk_size:3000}': ['rate>=0.99'],
        'bulk_publish_success_rate{bulk_size:5000}': ['rate>=0.99'],

        // ③ e2e 지연 (SSE timeout 90s 기준, 오차 포함)
        'e2e_latency_ms': ['p(99)<10000'],
    },
};

// ── A. SSE 유지 VU ───────────────────────────────────────────────────
let sseErrorLogged = 0;
const SSE_ERROR_LOG_MAX = 20; // 전체 VU 24K에서 로그 폭발 방지

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
                // SSE 연결은 name:sse_stream 으로 격리 — http_req_failed 오염 방지
                tags:    { name: 'sse_stream' },
                timeout: '110s',
            }
        );

        const ok       = res.status === 200 || (res.status === 0 && res.error_code === 1050);
        const poolFull = res.status === 503  || res.error_code === 1212;

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
            if (sseErrorLogged < SSE_ERROR_LOG_MAX) {
                console.log(`SSE disconnect memberId=${memberId} status=${res.status} err=${res.error}`);
                sseErrorLogged++;
            }
            sleep(2);
            continue;
        }

        sleep(1);
    }
}

// ── B. 부서 단위 발송 VU ─────────────────────────────────────────────
let _stageStartedAt = 0;

export function sendBulk() {
    if (_stageStartedAt === 0) _stageStartedAt = Date.now();

    const bulkSize      = currentBulkSize();
    const tags          = { name: 'bulk_publish', bulk_size: String(bulkSize) };

    // startMemberId 범위 초과 방지: start + count ≤ SSE_VUS
    const maxStart      = SSE_VUS - bulkSize;
    const startMemberId = SEED_OFFSET + ((__VU - 1) * bulkSize % Math.max(maxStart, 1)) + 1;
    const publishedAt   = Date.now();

    const res = http.post(
        `${NOTIFICATION_URL}/module/notifications/publish-k6-bulk`,
        JSON.stringify({ startMemberId, count: bulkSize, eventTypeCode: 'PAYMENT_COMPLETED', publishedAt }),
        { headers: HEADERS_JSON, tags }
    );

    bulkPublishDuration.add(res.timings.duration, { bulk_size: String(bulkSize) });
    const ok = check(res, { 'bulk publish 200': (r) => r.status === 200 });
    bulkPublishRate.add(ok, { bulk_size: String(bulkSize) });

    if (ok) {
        bulkPublishSuccess.add(1, { bulk_size: String(bulkSize) });
        bulkEnqueuedTotal.add(bulkSize);
    } else {
        bulkPublishFailed.add(1, { bulk_size: String(bulkSize) });
        console.log(`bulk publish fail bulk_size=${bulkSize} status=${res.status} body=${(res.body || '').slice(0, 200)}`);
    }

    sleep(stageSleepSec(bulkSize));
}

// ── 현재 단계 bulkSize 결정 ──────────────────────────────────────────
function currentBulkSize() {
    const elapsedMs = Date.now() - _stageStartedAt;
    let accumulated = 0;
    for (const stage of STAGES) {
        accumulated += parseDurationSec(stage.duration) * 1000;
        if (elapsedMs < accumulated) return stage.bulkSize;
    }
    return STAGES[STAGES.length - 1].bulkSize;
}

// bulkSize별 발송 간격 (VU 10개 기준 서버 부하 조절)
function stageSleepSec(bulkSize) {
    if (bulkSize <= 500)  return 2;
    if (bulkSize <= 1000) return 3;
    if (bulkSize <= 3000) return 5;
    return 8;
}

// ── SSE body e2e 지연 파싱 ───────────────────────────────────────────
// http.get은 연결 종료 후 body를 읽으므로 측정 오차가 SSE timeout(90s)만큼 존재.
// 절대 시각(publishedAt) 기반이라 오차는 지연값에 반영됨 — 경향 파악용으로 유효.
function parseE2eLatency(body) {
    if (!body) return;
    const now = Date.now();
    const dataLines = body.match(/^data:\s*(\{.+\})$/gm) || [];
    for (const line of dataLines) {
        try {
            const outer = JSON.parse(line.replace(/^data:\s*/, ''));
            let publishedAt = null;
            if (outer.payload && typeof outer.payload === 'string') {
                publishedAt = JSON.parse(outer.payload).publishedAt;
            } else if (typeof outer.publishedAt === 'number') {
                publishedAt = outer.publishedAt;
            }
            if (publishedAt) {
                e2eLatency.add(now - publishedAt);
                e2eReceived.add(1);
            }
        } catch (_) {}
    }
}

function parseDurationSec(d) {
    if (d.endsWith('m')) return parseInt(d) * 60;
    if (d.endsWith('s')) return parseInt(d);
    return 60;
}

// k6/notification/prometheus-verify.js
// Prometheus 메트릭 적재 검증 테스트
//
// 목적:
//   1. SSE 연결 → 알림 이벤트 발행 → Prometheus에 메트릭이 실제로 수집되는지 검증
//   2. 수집 대상: sse_active_connections, http_server_requests, jvm_memory_used
//   3. 부하는 최대 100 VU 이내로 제한
//
// 검증 흐름:
//   A. SSE 구독자 50명 연결 유지 (1분 30초)
//   B. 알림 이벤트 30 TPS 발행 (1분)
//   C. Prometheus HTTP API로 실제 수집 값 확인 (setup/teardown)
//
// 실행:
//   PROMETHEUS_URL=http://localhost:9090 ./run.sh prometheus-verify
//   또는 단독 실행:
//   PROMETHEUS_URL=http://localhost:9090 k6 run k6/notification/prometheus-verify.js \
//     -e BASE_URL=http://localhost:8080 \
//     -e PROMETHEUS_URL=http://localhost:9090 \
//     -e K6_TOKENS_FILE=k6/notification/tokens.json

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';
import { fetchTokens, getTokenFromCache, getMemberId, preloadedTokens } from '../helpers/token.js';
import { connectSseStream, classifySseResult } from '../helpers/sse.js';
import { publishPayment } from '../helpers/publish.js';

const BASE_URL        = __ENV.BASE_URL        || 'http://localhost:8080';
const PROMETHEUS_URL  = __ENV.PROMETHEUS_URL  || 'http://localhost:9090';
const SEED_OFFSET     = parseInt(__ENV.K6_SEED_OFFSET || '10000', 10);

const SSE_VUS     = 50;
const PUBLISH_VUS = 20;

const sseConnectSuccess = new Counter('pv_sse_connect_success');
const sseConnectFailed  = new Counter('pv_sse_connect_failed');
const publishSuccess    = new Counter('pv_publish_success');
const publishFailed     = new Counter('pv_publish_failed');
const promQuerySuccess  = new Rate('pv_prometheus_query_success');

export const options = {
    insecureSkipTLSVerify: true,
    discardResponseBodies: false,
    scenarios: {
        // A. SSE 연결 유지 — Prometheus sse_active_connections 수집 확인용
        sse_subscribers: {
            executor:  'constant-vus',
            vus:       SSE_VUS,
            duration:  '1m30s',
            exec:      'maintainSse',
            startTime: '0s',
        },
        // B. 알림 이벤트 발행 — http_server_requests 수집 확인용
        event_publisher: {
            executor:        'constant-arrival-rate',
            rate:            30,
            timeUnit:        '1s',
            preAllocatedVUs: PUBLISH_VUS,
            maxVUs:          PUBLISH_VUS,
            duration:        '1m',
            exec:            'publishEvent',
            startTime:       '15s',
        },
        // C. Prometheus 쿼리 검증 — 스크랩 주기(15s) 이후 실행
        prometheus_checker: {
            executor:        'constant-arrival-rate',
            rate:            1,
            timeUnit:        '30s',
            preAllocatedVUs: 2,
            maxVUs:          2,
            duration:        '1m',
            exec:            'verifyPrometheus',
            startTime:       '45s',
        },
    },
    thresholds: {
        pv_sse_connect_failed:       ['count<5'],
        pv_publish_failed:           ['count<10'],
        pv_prometheus_query_success: ['rate>=1.0'],
    },
    summaryTrendStats: ['avg', 'p(95)', 'max'],
};

export function setup() {
    const tokens = preloadedTokens.length > 0
        ? preloadedTokens
        : fetchTokens(BASE_URL, SEED_OFFSET, SSE_VUS + PUBLISH_VUS);
    console.log(`토큰 준비 완료: ${tokens.length}개`);

    // 테스트 시작 전 Actuator 접근 확인
    const actuatorRes = http.get(`${BASE_URL}/actuator/prometheus`, { timeout: '10s' });
    if (actuatorRes.status !== 200) {
        console.warn(`[warn] /actuator/prometheus 응답: ${actuatorRes.status} — 인증 설정 확인 필요`);
    } else {
        console.log('[ok] /actuator/prometheus 접근 확인');
    }

    return { tokens };
}

// ── A. SSE 연결 유지 ─────────────────────────────────────────────────────────
export function maintainSse(data) {
    const token = getTokenFromCache(data.tokens, __VU);
    if (!token) {
        sseConnectFailed.add(1);
        return;
    }

    // 30초 연결 유지 후 재연결
    const res  = connectSseStream(token, 30);
    const kind = classifySseResult(res);

    if (kind === 'failed') {
        sseConnectFailed.add(1);
        return;
    }
    sseConnectSuccess.add(1);
    sleep(1);
}

// ── B. 알림 이벤트 발행 ───────────────────────────────────────────────────────
export function publishEvent() {
    const memberId = getMemberId((__ITER % SSE_VUS) + 1);
    const res      = publishPayment(memberId, __ITER);

    if (res.status === 200) {
        publishSuccess.add(1);
    } else {
        publishFailed.add(1);
    }
}

// ── C. Prometheus 쿼리 검증 ───────────────────────────────────────────────────
export function verifyPrometheus() {
    // ── 1. sse_active_connections ──────────────────────────────────────────
    const sseRes = queryPrometheus('sse_active_connections');
    const sseOk  = check(sseRes, {
        'sse_active_connections 수집됨':    (r) => r.status === 200,
        'sse_active_connections 값 > 0':    (r) => extractFirstValue(r.body) > 0,
    });
    promQuerySuccess.add(sseOk);
    if (!sseOk) {
        const val = extractFirstValue(sseRes.body);
        console.warn(`[fail] sse_active_connections=${val} (기대: >0, status=${sseRes.status})`);
    } else {
        console.log(`[ok] sse_active_connections=${extractFirstValue(sseRes.body)}`);
    }

    // ── 2. http_server_requests_seconds_count (SSE endpoint) ───────────────
    const httpRes = queryPrometheus(
        'http_server_requests_seconds_count{uri="/api/notifications/stream"}'
    );
    const httpOk = check(httpRes, {
        'http_server_requests(SSE) 수집됨': (r) => r.status === 200,
        'http_server_requests(SSE) 값 > 0': (r) => extractFirstValue(r.body) > 0,
    });
    promQuerySuccess.add(httpOk);
    if (!httpOk) {
        console.warn(`[fail] http_server_requests SSE endpoint 미수집 (status=${httpRes.status})`);
    } else {
        console.log(`[ok] http_server_requests(SSE)=${extractFirstValue(httpRes.body)}`);
    }

    // ── 3. jvm_memory_used_bytes ────────────────────────────────────────────
    const jvmRes = queryPrometheus('jvm_memory_used_bytes{area="heap"}');
    const jvmOk  = check(jvmRes, {
        'jvm_memory_used_bytes 수집됨':    (r) => r.status === 200,
        'jvm_memory_used_bytes 값 > 0':    (r) => extractFirstValue(r.body) > 0,
    });
    promQuerySuccess.add(jvmOk);
    if (!jvmOk) {
        console.warn(`[fail] jvm_memory_used_bytes 미수집 (status=${jvmRes.status})`);
    } else {
        const mb = Math.round(extractFirstValue(jvmRes.body) / 1024 / 1024);
        console.log(`[ok] jvm_heap_used=${mb}MB`);
    }

    // ── 4. hikaricp_connections_active ─────────────────────────────────────
    const hikariRes = queryPrometheus('hikaricp_connections_active');
    const hikariOk  = check(hikariRes, {
        'hikaricp_connections_active 수집됨': (r) => r.status === 200,
    });
    promQuerySuccess.add(hikariOk);
    if (hikariOk) {
        console.log(`[ok] hikaricp_connections_active=${extractFirstValue(hikariRes.body)}`);
    }
}

function queryPrometheus(promql) {
    const encoded = encodeURIComponent(promql);
    return http.get(
        `${PROMETHEUS_URL}/api/v1/query?query=${encoded}`,
        { timeout: '10s', tags: { type: 'prometheus' } }
    );
}

function extractFirstValue(body) {
    if (!body) return 0;
    try {
        const parsed = JSON.parse(body);
        const result = parsed && parsed.data && parsed.data.result;
        if (!result || result.length === 0) return 0;
        return parseFloat(result[0].value[1]) || 0;
    } catch (_) {
        return 0;
    }
}

export function teardown(data) {
    console.log('\n========= Prometheus 적재 최종 확인 =========');

    const metrics = [
        'sse_active_connections',
        'http_server_requests_seconds_count{uri="/api/notifications/stream"}',
        'jvm_memory_used_bytes{area="heap"}',
        'hikaricp_connections_active',
        'hikaricp_connections_pending',
        'notification_batch_buffer_size',
    ];

    for (const metric of metrics) {
        const res = queryPrometheus(metric);
        if (res.status !== 200) {
            console.warn(`  [miss] ${metric} — HTTP ${res.status}`);
            continue;
        }
        const val = extractFirstValue(res.body);
        if (val === 0) {
            console.warn(`  [zero] ${metric} = 0 (미수집 또는 값 없음)`);
        } else {
            console.log(`  [ok]   ${metric} = ${val}`);
        }
    }

    console.log('=============================================\n');
}

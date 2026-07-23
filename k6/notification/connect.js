// k6/notification/connect.js
// SSE 연결 수립 속도 테스트
//
// 측정 범위:
//   - SSE endpoint TTFB (첫 번째 헤더 수신까지)
//   - 연결 수락 성공률 (3s 안에 200 응답)
//   - 초당 N개 연결 요청 시 서버 수락 능력
//
// 목표:
//   3,000 CCU 달성 가능 여부 확인
//   실행: .\run-windows.ps1 -Target connect -Vus 3000
//
// connect vs sustain:
//   connect — 연결을 맺고 즉시 끊는다. 수락 속도/성공률 측정.
//   sustain — 연결을 길게 유지한다. CCU 수용량 측정.
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { fetchTokens, getTokenFromCache, preloadedTokens } from '../helpers/token.js';
import { connectSseStream, classifySseResult } from '../helpers/sse.js';

const BASE_URL    = __ENV.BASE_URL    || 'http://localhost:8080';
const MEMBER_URL  = __ENV.MEMBER_URL  || 'http://localhost:8084';
const PEAK_VUS    = parseInt(__ENV.MAX_VUS || '3000', 10);
const SEED_OFFSET = parseInt(__ENV.K6_SEED_OFFSET || '10000', 10);

const sseConnectSuccess = new Counter('sse_connect_success');
const sseConnectFailed  = new Counter('sse_connect_failed');
const sseAcceptLatency  = new Trend('sse_accept_latency', true);

export const options = {
    insecureSkipTLSVerify: true,
    discardResponseBodies: true,
    scenarios: {
        connect_ramp: {
            executor: 'ramping-arrival-rate',
            startRate: 10,
            timeUnit: '1s',
            stages: [
                { duration: '30s', target: 100  },
                { duration: '1m',  target: 500  },
                { duration: '30s', target: 1000 },
                { duration: '1m',  target: 1000 },
                { duration: '30s', target: 2000 },
                { duration: '1m',  target: 2000 },
                { duration: '30s', target: 3000 },
                { duration: '1m',  target: 3000 },
                { duration: '30s', target: 0    },
            ],
            preAllocatedVUs: 200,
            maxVUs: PEAK_VUS,
        },
    },
    thresholds: {
        http_req_failed:    ['rate<0.05'],
        sse_connect_failed: ['rate<0.05'],
        sse_accept_latency: ['p(95)<1000'],
    },
};

export function setup() {
    const tokens = preloadedTokens.length > 0
        ? preloadedTokens
        : fetchTokens(MEMBER_URL, SEED_OFFSET, PEAK_VUS);
    console.log(`토큰 준비 완료: ${tokens.length}개`);
    return { tokens };
}

export default function (data) {
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

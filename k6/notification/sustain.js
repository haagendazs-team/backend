// k6/notification/sustain.js
// SSE 연결 유지 부하 테스트 (CCU 수용량)
//
// 측정 범위:
//   - N개의 SSE 연결을 동시에 유지할 수 있는가 (CCU 수용량)
//   - 연결 유지 중 서버가 연결을 끊지 않는가 (비정상 종료 감지)
//
// 실행 방식:
//   .\run-windows.ps1 -Target sustain           → 8,000 → 20,000 (기본)
//   .\run-windows.ps1 -Target sustain -Vus 15000
//
// 스테이지 구조:
//   1. 0 → START_VUS 램프업 (2분)
//   2. START_VUS 유지 (2분) — 기준선 수렴 확인
//   3. START_VUS → MAX_VUS 1,000씩 증가, 단계마다 20초 램프업 + 40초 유지
//   4. MAX_VUS 유지 (1분) — 피크 안정성 확인
//   5. → 0 램프다운 (30초)
//
// SSE timeout(180s)에 맞게 VU는 연결 후 재연결을 반복한다.
// completed(error_code 1050) = 정상 timeout → 재연결
// failed = 서버 거부 → sseServerDisconnect 카운트 후 재연결
import { sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { getTokenFromCache, preloadedTokens } from '../helpers/token.js';
import { connectSseStream, classifySseResult } from '../helpers/sse.js';

const BASE_URL    = __ENV.BASE_URL || 'https://localhost:8443';
const MAX_VUS     = parseInt(__ENV.MAX_VUS || '20000', 10);
const START_VUS   = parseInt(__ENV.START_VUS || '8000', 10);
const SEED_OFFSET = parseInt(__ENV.K6_SEED_OFFSET || '10000', 10);
const STEP        = 1000;

function buildStepStages(start, max, step) {
    const stages = [];
    for (let target = start + step; target <= max; target += step) {
        stages.push({ duration: '20s', target });
        stages.push({ duration: '40s', target });
    }
    return stages;
}

const stepStages = buildStepStages(START_VUS, MAX_VUS, STEP);

const sseSessionCompleted    = new Counter('sse_session_completed');
const sseServerDisconnect    = new Counter('sse_server_disconnect');
const ssePoolExhausted       = new Counter('sse_pool_exhausted');
const sseDurationTotalMs     = new Counter('sse_duration_total_ms');
const sseConnectAttempts     = new Counter('sse_connect_attempts');
const sseConnectSuccess      = new Counter('sse_connect_success');

export const options = {
    insecureSkipTLSVerify: true,
    discardResponseBodies: true,
    scenarios: {
        sustain_ramp: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '2m',  target: START_VUS },
                { duration: '1m',  target: START_VUS },
                ...stepStages,
                { duration: '2m',  target: MAX_VUS },
                { duration: '30s', target: 0 },
            ],
            gracefulRampDown: '30s',
        },
    },
    thresholds: {
        sse_server_disconnect: ['count<1'],
        sse_pool_exhausted:    ['count<10'],
    },
    summaryTrendStats: ['avg', 'p(95)', 'max'],
};

export function setup() {
    console.log(`토큰 준비 완료: ${preloadedTokens.length}개`);
    if (preloadedTokens.length === 0) {
        throw new Error('토큰 없음: K6_TOKENS_FILE 확인 필요');
    }
    return {};
}

export default function () {
    if (__VU > preloadedTokens.length) {
        ssePoolExhausted.add(1);
        return;
    }

    const token = getTokenFromCache(preloadedTokens, __VU);

    while (true
            ) {
        const sessionStart = Date.now();

        sseConnectAttempts.add(1);
        const res  = connectSseStream(token, 190 + Math.floor(Math.random() * 11));
        const kind = classifySseResult(res);

        sseDurationTotalMs.add(Date.now() - sessionStart);

        if (kind === 'completed') {
            sseConnectSuccess.add(1);
            sseSessionCompleted.add(1);
            sleep(1);
            continue;
        }

        sseServerDisconnect.add(1);
        sleep(3);
    }
}

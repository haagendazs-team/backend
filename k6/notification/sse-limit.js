// k6/notification/sse-limit.js
// SSE SLA 검증 — CCU 단계적 증가 + 단계별 에러율 감시 + 자동 abort
//
// 단계: 300→500→1000→1500→3000→4000→...→20000
// 각 단계 60s 유지 / 총 ~32분 / 30분 timeout 가드
//
// SLA 목표 (단계별 윈도우):
//   - API P95  < 300ms
//   - API avg  < 200ms
//   - 에러율   < 1%  → 초과 시 즉시 abort + cleanup
//
// 실행:
//   ./k6/notification/run.sh sse-limit [최대VU수]

import { sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';
import exec from 'k6/execution';
import { getPreloadedTokens, getTokenFromCache, getMemberId } from '../helpers/token.js';
import { connectSseStream, classifySseResult, countNotificationEvents } from '../helpers/sse.js';
import { publishPayment } from '../helpers/publish.js';
const MAX_VUS  = parseInt(__ENV.MAX_VUS || '20000', 10);

// ── CCU 유지 메트릭 ──────────────────────────────────────────────────────────
const sseConnectSuccess   = new Counter('sse_connect_success');
const sseConnectFailed    = new Counter('sse_connect_failed');
const sseServerDisconnect = new Counter('sse_server_disconnect');
const sseSessionCompleted = new Counter('sse_session_completed');
const sseAcceptLatency    = new Trend('sse_accept_latency', true);

// ── 발송 메트릭 ──────────────────────────────────────────────────────────────
const publishSuccess     = new Counter('notify_publish_success');
const publishFailed      = new Counter('notify_publish_failed');
const publishSuccessRate = new Rate('notify_publish_success_rate');
const publishDuration    = new Trend('notify_publish_duration', true);

// ── 수신 메트릭 ──────────────────────────────────────────────────────────────
const receiveEventsReceived = new Counter('receive_events_received');
const receiveEventsDropped  = new Counter('receive_events_dropped');
const receiveDeliveryRate   = new Rate('receive_delivery_rate');

// ── 단계별 에러 윈도우 (공유 상태) ───────────────────────────────────────────
// 각 단계 시작 시 초기화 — 해당 단계의 발행 요청만 카운트
const windowTotal  = new Counter('window_total');
const windowFailed = new Counter('window_failed');

// ── 단계 구성 ────────────────────────────────────────────────────────────────
const RAMP_SEC    = 30;
const SUSTAIN_SEC = 60;

const ALL_STEPS = [300, 500, 1000, 1500, 3000, 4000, 5000, 6000, 7000, 8000, 9000,
                   10000, 11000, 12000, 13000, 14000, 15000, 16000, 17000, 18000, 19000, 20000];
const SLA_STEPS = ALL_STEPS.filter(s => s <= MAX_VUS);

function buildStages() {
    const steps  = SLA_STEPS.length > 0 ? SLA_STEPS : [MAX_VUS];
    const stages = [];

    stages.push({ duration: `${RAMP_SEC}s`, target: steps[0] });

    for (let i = 0; i < steps.length; i++) {
        stages.push({ duration: `${SUSTAIN_SEC}s`, target: steps[i] });
        if (i + 1 < steps.length) {
            stages.push({ duration: `${RAMP_SEC}s`, target: steps[i + 1] });
        }
    }

    stages.push({ duration: '30s', target: 0 });
    return stages;
}

const ccuStages = buildStages();

function totalDurationSec() {
    return ccuStages.reduce((sum, s) => sum + parseInt(s.duration), 0);
}

// init context에서 토큰 수 검증
const tokens = getPreloadedTokens();
const maxStageTarget = ccuStages.reduce((m, s) => Math.max(m, s.target), 0);
if (tokens.length < maxStageTarget) {
    throw new Error(
        `토큰 부족: tokens.length=${tokens.length}, 최대 스테이지 목표=${maxStageTarget}. ` +
        `./run.sh sse-limit ${maxStageTarget} 으로 재실행하세요.`
    );
}

const RECEIVE_VUS = Math.min(100, Math.floor(MAX_VUS * 0.05));

export const options = {
    discardResponseBodies: true,
    scenarios: {
        sustain_ccu: {
            executor:         'ramping-vus',
            startVUs:         0,
            stages:           ccuStages,
            gracefulRampDown: '30s',
            exec:             'sustainSse',
            startTime:        '0s',
        },
        publish_single: {
            executor:        'constant-arrival-rate',
            rate:            50,
            timeUnit:        '1s',
            preAllocatedVUs: 20,
            maxVUs:          100,
            duration:        `${totalDurationSec() - RAMP_SEC}s`,
            exec:            'publishSingle',
            startTime:       `${RAMP_SEC}s`,
        },
        // 메모리 부족으로 비활성화: res.body를 VU×스트림 크기만큼 메모리에 올려 OOM 유발
        // receive_verify: {
        //     executor:  'constant-vus',
        //     vus:       RECEIVE_VUS,
        //     duration:  `${totalDurationSec() - RAMP_SEC}s`,
        //     exec:      'receiveAndVerify',
        //     startTime: `${RAMP_SEC}s`,
        // },
    },

    thresholds: {
        // 누적 SLA — Grafana 대시보드용
        notify_publish_duration:     ['avg<200', 'p(95)<300'],
        notify_publish_success_rate: ['rate>=0.99'],
        sse_connect_failed:          ['count<500'],
        sse_accept_latency:          ['p(95)<5000'],
        // receive_delivery_rate:       ['rate>=0.70'],  // receive_verify 비활성화 중
    },
};

// ── 단계 경계 계산 (sustain 시작 시각 목록) ──────────────────────────────────
// 각 sustain 구간 시작 시각(초)을 미리 계산해 윈도우 리셋 타이밍으로 사용
const stageBoundaries = (() => {
    const boundaries = [];
    let elapsed = 0;
    for (const s of ccuStages) {
        const sec = parseInt(s.duration);
        // ramp 구간이 아닌 sustain 구간 시작점만 수집
        if (sec === SUSTAIN_SEC) {
            boundaries.push({ start: elapsed, end: elapsed + sec, target: s.target });
        }
        elapsed += sec;
    }
    return boundaries;
})();

// 현재 시각이 속한 sustain 단계 인덱스 반환
function currentStageIndex(elapsedSec) {
    for (let i = 0; i < stageBoundaries.length; i++) {
        if (elapsedSec >= stageBoundaries[i].start && elapsedSec < stageBoundaries[i].end) {
            return i;
        }
    }
    return -1;
}

// VU별 마지막으로 집계한 단계 인덱스 추적 (단계 전환 시 윈도우 카운터 리셋)
// k6는 VU별 독립 실행 — __VU로 구분
const vuLastStage = {};
const stageWindowSuccess = {};
const stageWindowTotal   = {};

function checkErrorRate(stageIdx, ok) {
    if (stageIdx < 0) return;

    if (stageWindowTotal[stageIdx] === undefined) {
        stageWindowSuccess[stageIdx] = 0;
        stageWindowTotal[stageIdx]   = 0;
    }

    stageWindowTotal[stageIdx]++;
    if (ok) stageWindowSuccess[stageIdx]++;

    // 최소 20건 이상 쌓인 후부터 판정
    const total = stageWindowTotal[stageIdx];
    if (total < 20) return;

    const errorRate = 1 - (stageWindowSuccess[stageIdx] / total);
    if (errorRate > 0.01) {
        const target = stageBoundaries[stageIdx] ? stageBoundaries[stageIdx].target : '?';
        console.error(
            `[ABORT] CCU ${target} 단계 에러율 ${(errorRate * 100).toFixed(2)}% > 1% ` +
            `(${total - stageWindowSuccess[stageIdx]}/${total})`
        );
        exec.test.abort(`CCU ${target} 단계에서 에러율 1% 초과`);
    }
}

// ── A. CCU 유지 (재연결 루프) ─────────────────────────────────────────────────
export function sustainSse() {
    if (__VU > tokens.length) return;

    const token = getTokenFromCache(tokens, __VU);

    while (true) {
        const sessionStart = Date.now();
        const res   = connectSseStream(token, randomIntBetween(30, 60));
        const kind  = classifySseResult(res);
        const elapsed = Date.now() - sessionStart;

        if (kind === 'accepted') {
            sseAcceptLatency.add(res.timings.waiting);
            sseConnectSuccess.add(1);
            sseSessionCompleted.add(1);
            sleep(1);
            continue;
        }

        if (kind === 'completed') {
            sseAcceptLatency.add(elapsed);
            sseConnectSuccess.add(1);
            sseSessionCompleted.add(1);
            sleep(1);
            continue;
        }

        sseConnectFailed.add(1);
        sseServerDisconnect.add(1);
        sleep(3);
    }
}

// ── B. 단건 발행 ─────────────────────────────────────────────────────────────
export function publishSingle() {
    const idx      = randomIntBetween(0, tokens.length - 1);
    const memberId = getMemberId(idx + 1);
    const res      = publishPayment(memberId, __ITER);

    publishDuration.add(res.timings.duration);

    const ok = res.status === 200;
    publishSuccessRate.add(ok);
    if (ok) publishSuccess.add(1);
    else    publishFailed.add(1);

    // 단계별 에러율 윈도우 체크
    const elapsedSec = exec.scenario.progress * totalDurationSec();
    const stageIdx   = currentStageIndex(elapsedSec);
    checkErrorRate(stageIdx, ok);
}

// ── C. 수신 검증 ─────────────────────────────────────────────────────────────
export function receiveAndVerify() {
    const token = getTokenFromCache(tokens, __VU);
    if (!token) {
        receiveEventsDropped.add(1);
        return;
    }

    const res  = connectSseStream(token, randomIntBetween(25, 50), true);
    const kind = classifySseResult(res);

    if (kind === 'failed') {
        receiveEventsDropped.add(1);
        receiveDeliveryRate.add(false);
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

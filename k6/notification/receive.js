// k6/notification/receive.js
// SSE 이벤트 수신 검증 + 전파 완전성 측정 + ping ack 보고
//
// 측정 범위:
//   - SSE 연결 유지 중 실제 이벤트 수신 여부
//   - 이벤트 누락률 (dropped / triggered)
//   - 수신 지연 (연결 ~ 이벤트 body 수신까지 소요 시간)
//   - ping ack 전송 후 ping-result 조회
//
// 파싱 방식 (중요):
//   k6 기본 http.get은 SSE 스트리밍을 차단 후 body 일괄 반환.
//   timeout 경과 후 body 전체에서 "event: notification" 패턴을 카운트.
//   → 개별 이벤트 도달 시각 측정 불가 (전체 소요 시간만 기록)
//   → 이벤트가 timeout 이전에 왔으면 body에 포함됨 (수신 여부는 측정 가능)
//
// 실행:
//   .\run-windows.ps1 -Target receive -Vus 3000
import { check } from 'k6';
import http from 'k6/http';
import { Counter, Rate, Trend } from 'k6/metrics';
import { fetchTokens, getTokenFromCache, getMemberId, preloadedTokens } from '../helpers/token.js';
import { connectSseStream, classifySseResult, countNotificationEvents, countPingEvents } from '../helpers/sse.js';
import { publishPayment } from '../helpers/publish.js';

const BASE_URL    = __ENV.BASE_URL   || 'http://localhost:8080';
const MEMBER_URL  = __ENV.MEMBER_URL || 'http://localhost:8084';
const SEED_OFFSET = parseInt(__ENV.K6_SEED_OFFSET || '10000', 10);

const sseEventsReceived  = new Counter('sse_events_received');
const sseEventsDropped   = new Counter('sse_events_dropped');
const ssePingReceived    = new Counter('sse_ping_received');
const ssePingAckSent     = new Counter('sse_ping_ack_sent');
const ssePingAckFailed   = new Counter('sse_ping_ack_failed');
const sseEventLatency    = new Trend('sse_event_latency', true);
const sseDeliveryRate    = new Rate('sse_delivery_rate');
const sseConnectFailed   = new Counter('sse_connect_failed');

const CCU_NORMAL     = 500;
const CCU_PEAK       = 1000;
const CCU_STRESS     = 1500;
const TRIGGER_VU_MAX = CCU_NORMAL;

export const options = {
    insecureSkipTLSVerify: true,
    scenarios: {
        receivers_normal: {
            executor:  'constant-vus',
            vus:       CCU_NORMAL,
            duration:  '6m',
            exec:      'receiveSse',
            startTime: '0s',
        },
        receivers_peak: {
            executor:  'constant-vus',
            vus:       CCU_PEAK,
            duration:  '4m',
            exec:      'receiveSse',
            startTime: '2m',
        },
        receivers_stress: {
            executor:  'constant-vus',
            vus:       CCU_STRESS,
            duration:  '2m',
            exec:      'receiveSse',
            startTime: '4m',
        },
        triggers: {
            executor:        'constant-arrival-rate',
            rate:            20,
            timeUnit:        '1s',
            preAllocatedVUs: 20,
            maxVUs:          40,
            duration:        '5m',
            exec:            'triggerNotification',
            startTime:       '10s',
        },
    },
    thresholds: {
        sse_delivery_rate:  ['rate>=0.80'],
        sse_connect_failed: ['count<150'],
    },
};

export function setup() {
    const totalVus = CCU_NORMAL + CCU_PEAK + CCU_STRESS;
    const tokens = preloadedTokens.length > 0
        ? preloadedTokens
        : fetchTokens(MEMBER_URL, SEED_OFFSET, totalVus);
    console.log(`토큰 준비 완료: ${tokens.length}개`);
    return { tokens };
}

export function receiveSse(data) {
    const token = getTokenFromCache(data.tokens, __VU);
    if (!token) {
        sseConnectFailed.add(1);
        return;
    }

    const connectTime = Date.now();
    const res         = connectSseStream(token, 25 + Math.floor(Math.random() * 26), true);
    const kind        = classifySseResult(res);

    if (kind === 'failed') {
        sseConnectFailed.add(1);
        return;
    }

    const connected = check(res, { 'SSE 연결 성공': () => kind !== 'failed' });
    if (!connected) {
        sseConnectFailed.add(1);
        return;
    }

    const body         = res.body || '';
    const eventCount   = countNotificationEvents(body);
    const pingCount    = countPingEvents(body);

    ssePingReceived.add(pingCount);

    if (pingCount > 0) {
        sendPingAck(token, pingCount);
    }

    if (eventCount > 0) {
        sseEventsReceived.add(eventCount);
        sseEventLatency.add(Date.now() - connectTime);
        sseDeliveryRate.add(true);
    } else {
        sseEventsDropped.add(1);
        sseDeliveryRate.add(false);
    }
}

function sendPingAck(token, pingCount) {
    const now = new Date().toISOString().replace('Z', '');
    const payload = JSON.stringify({
        status: '성공',
        pingReceivedAt: now,
    });
    const ackRes = http.post(
        `${BASE_URL}/api/notifications/stream/ack`,
        payload,
        {
            headers: {
                'Content-Type': 'application/json',
                Authorization: `Bearer ${token}`,
            },
            timeout: '5s',
        }
    );
    if (ackRes.status === 204) {
        ssePingAckSent.add(pingCount);
        fetchPingResult(token);
    } else {
        ssePingAckFailed.add(1);
    }
}

function fetchPingResult(token) {
    const res = http.get(
        `${BASE_URL}/api/notifications/stream/ping-result`,
        {
            headers: { Authorization: `Bearer ${token}` },
            timeout: '5s',
        }
    );
    check(res, {
        'ping-result 200': (r) => r.status === 200,
        'ping-result 총전송받은횟수 존재': (r) => {
            try {
                const body = JSON.parse(r.body);
                return typeof body['총전송받은횟수'] === 'number';
            } catch (_) {
                return false;
            }
        },
    });
}

export function triggerNotification() {
    const receiverVuId = (__ITER % TRIGGER_VU_MAX) + 1;
    const memberId     = getMemberId(receiverVuId);
    const res          = publishPayment(memberId, __ITER);
    check(res, { '알림 트리거 성공 (200)': (r) => r.status === 200 });
}

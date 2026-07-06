// k6/notification/send.js
// 알림 이벤트 전송 경로 부하 테스트
//
// 측정 범위:
//   - /module/notifications/publish 엔드포인트 처리량 (Redis XADD 경로)
//   - PAYMENT_COMPLETED 단건 TPS 내성
//
// 트래픽 산정:
//   - 평시 TPS:  ~50 TPS
//   - 피크 TPS:  ~300 TPS
//   - 스트레스:   500 TPS (한계 탐색)
//
// 실행:
//   .\run-windows.ps1 -Target send
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { getMemberId } from '../helpers/token.js';
import { publishPayment } from '../helpers/publish.js';

const PEAK_VUS = parseInt(__ENV.MAX_VUS || '3000', 10);

const publishSuccess     = new Counter('notify_publish_success');
const publishFailed      = new Counter('notify_publish_failed');
const publishSuccessRate = new Rate('notify_publish_success_rate');
const publishDuration    = new Trend('notify_publish_duration', true);

export const options = {
    insecureSkipTLSVerify: true,
    scenarios: {
        singleTargetSender: {
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
            exec: 'sendSingleTarget',
        },
    },
    thresholds: {
        notify_publish_success_rate: ['rate>=0.99'],
        notify_publish_duration:     ['p(95)<300'],
        http_req_failed:             ['rate<0.05'],
    },
};

export function setup() {
    return {};
}

export function sendSingleTarget() {
    const memberId = getMemberId((__VU - 1) % PEAK_VUS + 1);
    const res      = publishPayment(memberId, __ITER);
    publishDuration.add(res.timings.duration);

    const ok = check(res, { 'payment 발행 성공 (200)': (r) => r.status === 200 });
    publishSuccessRate.add(ok);
    if (ok) publishSuccess.add(1);
    else    publishFailed.add(1);
}

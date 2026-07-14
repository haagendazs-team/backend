// k6/notification/send.js
// 알림 이벤트 전송 경로 부하 테스트
//
// 측정 범위:
//   - /dev/events/publish 엔드포인트 처리량 (Redis XADD 경로)
//   - 단건(payment.completed) TPS 내성
//   - 브로드캐스트(ticket.opened) 팬아웃 DB I/O 내성
//
// 트래픽 산정:
//   - 평시 TPS:  ~50 TPS
//   - 피크 TPS:  ~300 TPS (티켓 오픈 순간)
//   - 스트레스:   500 TPS (한계 탐색)
//
// 브로드캐스트 주의:
//   ticket.opened 1회 = 전체 구독자 수 × DB I/O
//   Hikari pool 10 커넥션 기준 포화 구간 탐색이 핵심 목적
//
// 실행:
//   .\run-windows.ps1 -Target send
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { getMemberId } from '../helpers/token.js';
import { publishPayment, publishBroadcast } from '../helpers/publish.js';

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
        broadcastSender: {
            executor: 'ramping-arrival-rate',
            startRate: 1,
            timeUnit: '1s',
            preAllocatedVUs: 10,
            maxVUs: 50,
            stages: [
                { duration: '30s', target: 1  },
                { duration: '1m',  target: 3  },
                { duration: '1m',  target: 5  },
                { duration: '30s', target: 0  },
            ],
            startTime: '1m',
            exec: 'sendBroadcast',
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

export function sendBroadcast() {
    const res = publishBroadcast(__ITER);
    publishDuration.add(res.timings.duration);

    const ok = check(res, { 'ticket 브로드캐스트 발행 성공 (200)': (r) => r.status === 200 });
    publishSuccessRate.add(ok);
    if (ok) publishSuccess.add(1);
    else    publishFailed.add(1);
}

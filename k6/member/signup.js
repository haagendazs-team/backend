// k6/member/signup.js
// 회원가입 처리량 · 지연시간 테스트 (매 요청 unique email)
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { BASE_URL, isSuccess, signup } from '../helpers/member.js';

const PEAK_VUS = parseInt(__ENV.MAX_VUS || '30', 10);
const RUN_ID = __ENV.K6_RUN_ID || `${Date.now()}`;
const signupOk = new Counter('member_signup_ok');
const signupFail = new Counter('member_signup_fail');
const signupLatency = new Trend('member_signup_latency', true);

export const options = {
    scenarios: {
        signup_ramp: {
            executor: 'ramping-arrival-rate',
            startRate: 2,
            timeUnit: '1s',
            stages: [
                { duration: '30s', target: 5 },
                { duration: '1m', target: 15 },
                { duration: '1m', target: Math.min(30, PEAK_VUS) },
                { duration: '30s', target: 0 },
            ],
            preAllocatedVUs: Math.min(20, PEAK_VUS),
            maxVUs: PEAK_VUS,
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.05'],
        member_signup_fail: ['rate<0.05'],
        member_signup_latency: ['p(95)<1000'],
    },
};

export function setup() {
    console.log(`BASE_URL=${BASE_URL}, RUN_ID=${RUN_ID}`);
    return {};
}

export default function () {
    const id = `${RUN_ID}-${__VU}-${__ITER}`;
    const email = `k6-signup-${id}@perf.test`;
    const nickname = `su${String(id).replace(/[^a-zA-Z0-9]/g, '').slice(0, 20)}`;
    const res = signup(email, 'PerfTest1!', nickname);
    signupLatency.add(res.timings.duration);

    const ok = check(res, {
        'signup 201': (r) => r.status === 201 && isSuccess(r, 201),
    });

    if (ok) signupOk.add(1);
    else signupFail.add(1);

    sleep(0.2);
}

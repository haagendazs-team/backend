// k6/member/login.js
// Auth 로그인 처리량 · 지연시간 테스트
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import {
    BASE_URL,
    SEED_OFFSET,
    SEED_PASSWORD,
    isSuccess,
    login,
    seedEmail,
} from '../helpers/member.js';

const PEAK_VUS = parseInt(__ENV.MAX_VUS || '50', 10);
const loginOk = new Counter('member_login_ok');
const loginFail = new Counter('member_login_fail');
const loginLatency = new Trend('member_login_latency', true);

export const options = {
    scenarios: {
        login_ramp: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: Math.min(10, PEAK_VUS) },
                { duration: '1m', target: Math.min(30, PEAK_VUS) },
                { duration: '1m', target: PEAK_VUS },
                { duration: '1m', target: PEAK_VUS },
                { duration: '30s', target: 0 },
            ],
            gracefulRampDown: '10s',
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.05'],
        member_login_fail: ['rate<0.05'],
        member_login_latency: ['p(95)<500'],
    },
};

export function setup() {
    console.log(`BASE_URL=${BASE_URL}, SEED_OFFSET=${SEED_OFFSET}, VUS=${PEAK_VUS}`);
    return {};
}

export default function () {
    const email = seedEmail((__VU - 1) % PEAK_VUS);
    const res = login(email, SEED_PASSWORD);
    loginLatency.add(res.timings.duration);

    const ok = check(res, {
        'login 200': (r) => isSuccess(r, 200),
        'accessToken 존재': (r) => {
            try {
                return !!r.json('data.accessToken');
            } catch (_) {
                return false;
            }
        },
    });

    if (ok) loginOk.add(1);
    else loginFail.add(1);

    sleep(0.3);
}

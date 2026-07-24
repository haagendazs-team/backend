// k6/member/profile.js
// GET/PATCH /members/me 프로파일 조회·수정 부하
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import {
    BASE_URL,
    authHeaders,
    getUser,
    isSuccess,
    loginSeedUsers,
    parseData,
} from '../helpers/member.js';

const PEAK_VUS = parseInt(__ENV.MAX_VUS || '50', 10);
const profileOk = new Counter('member_profile_ok');
const profileFail = new Counter('member_profile_fail');
const profileLatency = new Trend('member_profile_latency', true);

export const options = {
    scenarios: {
        profile_load: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '20s', target: Math.min(10, PEAK_VUS) },
                { duration: '1m', target: PEAK_VUS },
                { duration: '1m', target: PEAK_VUS },
                { duration: '20s', target: 0 },
            ],
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.05'],
        member_profile_fail: ['rate<0.05'],
        member_profile_latency: ['p(95)<300'],
    },
};

export function setup() {
    const users = loginSeedUsers(PEAK_VUS);
    console.log(`토큰 준비: ${users.length}명`);
    return { users };
}

export default function (data) {
    const user = getUser(data.users, __VU);
    const opts = authHeaders(user.token);

    const getRes = http.get(`${BASE_URL}/members/me`, {
        ...opts,
        tags: { name: 'get_me' },
    });
    profileLatency.add(getRes.timings.duration);

    const getOk = check(getRes, {
        'GET /members/me 200': (r) => isSuccess(r, 200),
    });

    const nickname = `k6u${__VU}${__ITER}`.slice(0, 20);
    const patchRes = http.patch(
        `${BASE_URL}/members/me`,
        JSON.stringify({ nickname }),
        { ...opts, tags: { name: 'patch_me' } },
    );
    profileLatency.add(patchRes.timings.duration);

    const patched = parseData(patchRes);
    const patchOk = check(patchRes, {
        'PATCH /members/me 200': (r) => isSuccess(r, 200),
        'nickname 반영': () => patched && patched.nickname === nickname,
    });

    if (getOk && patchOk) profileOk.add(1);
    else profileFail.add(1);

    sleep(0.2);
}

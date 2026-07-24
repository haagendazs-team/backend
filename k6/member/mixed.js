// k6/member/mixed.js
// 실제 사용 패턴에 가까운 혼합 부하 (login / profile / workspace / channel)
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import {
    BASE_URL,
    SEED_PASSWORD,
    authHeaders,
    getUser,
    isSuccess,
    login,
    loginSeedUsers,
    parseData,
    seedEmail,
} from '../helpers/member.js';

const PEAK_VUS = parseInt(__ENV.MAX_VUS || '50', 10);
const mixedOk = new Counter('member_mixed_ok');
const mixedFail = new Counter('member_mixed_fail');
const mixedLatency = new Trend('member_mixed_latency', true);

export const options = {
    scenarios: {
        mixed_load: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: Math.min(10, PEAK_VUS) },
                { duration: '1m', target: Math.min(30, PEAK_VUS) },
                { duration: '2m', target: PEAK_VUS },
                { duration: '1m', target: PEAK_VUS },
                { duration: '30s', target: 0 },
            ],
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.05'],
        member_mixed_fail: ['rate<0.08'],
        member_mixed_latency: ['p(95)<800'],
    },
};

export function setup() {
    const users = loginSeedUsers(PEAK_VUS);
    console.log(`토큰 준비: ${users.length}명, BASE_URL=${BASE_URL}`);
    return { users };
}

export default function (data) {
    const roll = Math.random();
    let ok = true;

    if (roll < 0.25) {
        // 로그인 재발급 경로
        const email = seedEmail((__VU - 1) % PEAK_VUS);
        const res = login(email, SEED_PASSWORD);
        mixedLatency.add(res.timings.duration);
        ok = check(res, { 'mixed login': (r) => isSuccess(r, 200) }) && ok;
    } else if (roll < 0.55) {
        // 프로필 조회
        const user = getUser(data.users, __VU);
        const res = http.get(`${BASE_URL}/members/me`, {
            ...authHeaders(user.token),
            tags: { name: 'get_me' },
        });
        mixedLatency.add(res.timings.duration);
        ok = check(res, { 'mixed get me': (r) => isSuccess(r, 200) }) && ok;
    } else if (roll < 0.8) {
        // 내 워크스페이스 목록
        const user = getUser(data.users, __VU);
        const res = http.get(`${BASE_URL}/workspaces/my`, {
            ...authHeaders(user.token),
            tags: { name: 'list_my_workspaces' },
        });
        mixedLatency.add(res.timings.duration);
        ok = check(res, { 'mixed workspaces': (r) => isSuccess(r, 200) }) && ok;
    } else {
        // 짧은 workspace + channel 생성/삭제
        const user = getUser(data.users, __VU);
        const opts = authHeaders(user.token);
        const wsRes = http.post(
            `${BASE_URL}/workspaces`,
            JSON.stringify({ name: `mix-${__VU}-${__ITER}` }),
            { ...opts, tags: { name: 'create_workspace' } },
        );
        mixedLatency.add(wsRes.timings.duration);
        const workspaceId = (parseData(wsRes) || {}).workspaceId;
        ok = check(wsRes, { 'mixed create ws': (r) => r.status === 201 }) && ok;

        if (workspaceId) {
            const chRes = http.post(
                `${BASE_URL}/workspaces/${workspaceId}/channels`,
                JSON.stringify({ name: `mix-ch-${__ITER}` }),
                { ...opts, tags: { name: 'create_channel' } },
            );
            mixedLatency.add(chRes.timings.duration);
            const channelId = (parseData(chRes) || {}).channelId;
            ok = check(chRes, { 'mixed create ch': (r) => r.status === 201 }) && ok;

            if (channelId) {
                http.del(`${BASE_URL}/channels/${channelId}`, null, opts);
            }
            http.del(`${BASE_URL}/workspaces/${workspaceId}`, null, opts);
        }
    }

    if (ok) mixedOk.add(1);
    else mixedFail.add(1);

    sleep(0.2 + Math.random() * 0.3);
}

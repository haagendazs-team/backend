// k6/member/channel.js
// Channel CRUD 시나리오 (워크스페이스 생성 → 채널 CRUD → 정리)
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

const PEAK_VUS = parseInt(__ENV.MAX_VUS || '30', 10);
const chOk = new Counter('member_channel_ok');
const chFail = new Counter('member_channel_fail');
const chLatency = new Trend('member_channel_latency', true);

export const options = {
    scenarios: {
        channel_flow: {
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
        member_channel_fail: ['rate<0.05'],
        member_channel_latency: ['p(95)<500'],
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
    let ok = true;

    const wsRes = http.post(
        `${BASE_URL}/workspaces`,
        JSON.stringify({ name: `ch-ws-${__VU}-${__ITER}` }),
        { ...opts, tags: { name: 'create_workspace' } },
    );
    const workspaceId = (parseData(wsRes) || {}).workspaceId;
    if (!workspaceId) {
        chFail.add(1);
        return;
    }

    const createRes = http.post(
        `${BASE_URL}/workspaces/${workspaceId}/channels`,
        JSON.stringify({ name: `channel-${__VU}-${__ITER}` }),
        { ...opts, tags: { name: 'create_channel' } },
    );
    chLatency.add(createRes.timings.duration);
    ok = check(createRes, {
        'POST channel 201': (r) => r.status === 201,
    }) && ok;

    const channelId = (parseData(createRes) || {}).channelId;
    if (!channelId) {
        http.del(`${BASE_URL}/workspaces/${workspaceId}`, null, opts);
        chFail.add(1);
        return;
    }

    const listRes = http.get(`${BASE_URL}/workspaces/${workspaceId}/channels`, {
        ...opts,
        tags: { name: 'list_channels' },
    });
    chLatency.add(listRes.timings.duration);
    ok = check(listRes, { 'GET channels 200': (r) => isSuccess(r, 200) }) && ok;

    const getRes = http.get(`${BASE_URL}/channels/${channelId}`, {
        ...opts,
        tags: { name: 'get_channel' },
    });
    chLatency.add(getRes.timings.duration);
    ok = check(getRes, { 'GET channel 200': (r) => isSuccess(r, 200) }) && ok;

    const membersRes = http.get(`${BASE_URL}/channels/${channelId}/members`, {
        ...opts,
        tags: { name: 'list_channel_members' },
    });
    chLatency.add(membersRes.timings.duration);
    ok = check(membersRes, {
        'GET channel members 200': (r) => isSuccess(r, 200),
    }) && ok;

    const delCh = http.del(`${BASE_URL}/channels/${channelId}`, null, {
        ...opts,
        tags: { name: 'delete_channel' },
    });
    chLatency.add(delCh.timings.duration);
    ok = check(delCh, { 'DELETE channel 204': (r) => r.status === 204 }) && ok;

    const delWs = http.del(`${BASE_URL}/workspaces/${workspaceId}`, null, {
        ...opts,
        tags: { name: 'delete_workspace' },
    });
    ok = check(delWs, { 'DELETE workspace 204': (r) => r.status === 204 }) && ok;

    if (ok) chOk.add(1);
    else chFail.add(1);

    sleep(0.3);
}

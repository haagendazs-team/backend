// k6/member/workspace.js
// Workspace CRUD 시나리오 (생성 → 조회 → 멤버 목록 → 삭제)
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
const wsOk = new Counter('member_workspace_ok');
const wsFail = new Counter('member_workspace_fail');
const wsLatency = new Trend('member_workspace_latency', true);

export const options = {
    scenarios: {
        workspace_flow: {
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
        member_workspace_fail: ['rate<0.05'],
        member_workspace_latency: ['p(95)<500'],
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

    const createRes = http.post(
        `${BASE_URL}/workspaces`,
        JSON.stringify({ name: `ws-k6-${__VU}-${__ITER}` }),
        { ...opts, tags: { name: 'create_workspace' } },
    );
    wsLatency.add(createRes.timings.duration);
    ok = check(createRes, { 'POST /workspaces 201': (r) => r.status === 201 }) && ok;

    const workspace = parseData(createRes);
    const workspaceId = workspace && workspace.workspaceId;
    if (!workspaceId) {
        wsFail.add(1);
        return;
    }

    const myRes = http.get(`${BASE_URL}/workspaces/my`, {
        ...opts,
        tags: { name: 'list_my_workspaces' },
    });
    wsLatency.add(myRes.timings.duration);
    ok = check(myRes, { 'GET /workspaces/my 200': (r) => isSuccess(r, 200) }) && ok;

    const getRes = http.get(`${BASE_URL}/workspaces/${workspaceId}`, {
        ...opts,
        tags: { name: 'get_workspace' },
    });
    wsLatency.add(getRes.timings.duration);
    ok = check(getRes, { 'GET /workspaces/{id} 200': (r) => isSuccess(r, 200) }) && ok;

    const membersRes = http.get(`${BASE_URL}/workspaces/${workspaceId}/members`, {
        ...opts,
        tags: { name: 'list_workspace_members' },
    });
    wsLatency.add(membersRes.timings.duration);
    ok = check(membersRes, {
        'GET /workspaces/{id}/members 200': (r) => isSuccess(r, 200),
    }) && ok;

    const delRes = http.del(`${BASE_URL}/workspaces/${workspaceId}`, null, {
        ...opts,
        tags: { name: 'delete_workspace' },
    });
    wsLatency.add(delRes.timings.duration);
    ok = check(delRes, { 'DELETE /workspaces/{id} 204': (r) => r.status === 204 }) && ok;

    if (ok) wsOk.add(1);
    else wsFail.add(1);

    sleep(0.3);
}

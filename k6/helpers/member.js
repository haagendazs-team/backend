import http from 'k6/http';
import { check } from 'k6';

/**
 * member 도메인 k6 헬퍼
 *
 * BASE_URL 기본값: Gateway 경유 (http://localhost:8080/api/members)
 * MEMBER_URL: member 서비스 직접 호출 (http://localhost:8084) — BASE_URL 미지정 시 fallback
 */
export const BASE_URL = (__ENV.BASE_URL || __ENV.MEMBER_URL || 'http://localhost:8080/api/members').replace(/\/$/, '');
export const SEED_PASSWORD = __ENV.K6_SEED_PASSWORD || 'PerfTest1!';
export const SEED_OFFSET = parseInt(__ENV.K6_SEED_OFFSET || '10000', 10);

export function seedEmail(index) {
    return `k6-member-${SEED_OFFSET + index}@perf.test`;
}

export function seedNickname(index) {
    return `k6user${SEED_OFFSET + index}`;
}

export function authHeaders(token) {
    return {
        headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${token}`,
        },
        tags: {},
    };
}

export function jsonHeaders(extraTags = {}) {
    return {
        headers: { 'Content-Type': 'application/json' },
        tags: extraTags,
    };
}

export function signup(email, password, nickname, tags = {}) {
    return http.post(
        `${BASE_URL}/auth/signup`,
        JSON.stringify({ email, password, nickname }),
        jsonHeaders({ name: 'signup', ...tags }),
    );
}

export function login(email, password, tags = {}) {
    return http.post(
        `${BASE_URL}/auth/login`,
        JSON.stringify({ email, password }),
        jsonHeaders({ name: 'login', ...tags }),
    );
}

export function parseToken(res) {
    try {
        const body = res.json();
        return body && body.data ? body.data.accessToken : null;
    } catch (_) {
        return null;
    }
}

export function parseData(res) {
    try {
        const body = res.json();
        return body && body.data !== undefined ? body.data : null;
    } catch (_) {
        return null;
    }
}

/**
 * setup()에서 seed 계정 N개 로그인 → [{ email, token, memberId }]
 */
export function loginSeedUsers(count) {
    const users = [];
    for (let i = 0; i < count; i++) {
        const email = seedEmail(i);
        const res = login(email, SEED_PASSWORD, { type: 'setup' });
        const ok = check(res, {
            'setup login 200': (r) => r.status === 200,
        });
        if (!ok) {
            throw new Error(`seed 로그인 실패 email=${email} status=${res.status} body=${res.body}`);
        }
        const data = parseData(res);
        users.push({
            email,
            token: data.accessToken,
            refreshToken: data.refreshToken,
        });
    }
    return users;
}

export function getUser(users, vuId) {
    return users[(vuId - 1) % users.length];
}

export function isSuccess(res, expectedStatus = 200) {
    if (res.status !== expectedStatus) return false;
    if (expectedStatus === 204) return true;
    try {
        const body = res.json();
        return body && body.success === true;
    } catch (_) {
        return false;
    }
}

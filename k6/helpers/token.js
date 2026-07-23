import http from 'k6/http';
import { SharedArray } from 'k6/data';

const SEED_COUNT = 10000;

const _tokensFile = __ENV.K6_TOKENS_FILE || '';
const preloadedTokens_ = _tokensFile
    ? new SharedArray('tokens', () => JSON.parse(open(_tokensFile)))
    : new SharedArray('tokens', () => []);

export const preloadedTokens = preloadedTokens_;

export function getMemberId(vuId) {
    const seedOffset = parseInt(__ENV.K6_SEED_OFFSET || '10000', 10);
    return seedOffset + (vuId % SEED_COUNT);
}

/**
 * setup() 컨텍스트에서 호출.
 * 파일이 이미 로드됐으면 그대로 반환, 없으면 member 서비스 bulk API 순차 호출.
 * MEMBER_URL: member 서비스 직접 주소 (Gateway /api/members 경유 시 JWT 검증으로 차단됨)
 */
export function fetchTokens(memberUrl, seedOffset, vus, expiryMs = 5400000) {
    if (preloadedTokens.length > 0) {
        return preloadedTokens;
    }

    const bulkSize = 500;
    const tokens = [];

    for (let offset = 0; offset < vus; offset += bulkSize) {
        const startId = seedOffset + offset + 1;
        const count = Math.min(bulkSize, vus - offset);
        const url = `${memberUrl}/dev/token/bulk?startMemberId=${startId}&count=${count}&role=USER&expiryMs=${expiryMs}`;
        const res = http.get(url, { timeout: '60s', tags: { type: 'setup' } });
        if (res.status !== 200) {
            throw new Error(`토큰 발급 실패 startMemberId=${startId} status=${res.status}`);
        }
        tokens.push(...JSON.parse(res.body));
    }

    return tokens;
}

/** 하위 호환용 */
export function getPreloadedTokens() {
    return preloadedTokens;
}

export function getTokenFromCache(tokens, vuId) {
    if (!tokens || tokens.length === 0) return null;
    const entry = tokens[(vuId - 1) % tokens.length];
    return entry && typeof entry === 'object' && entry.token ? entry.token : entry;
}

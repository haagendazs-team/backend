import http from 'k6/http';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

/**
 * SSE 연결 결과 분류
 *   'completed'   — k6 timeout (error_code 1050): 정상 유지 후 만료
 *   'accepted'    — 서버가 200 즉시 반환
 *   'failed'      — 서버 거부 또는 네트워크 오류
 */
export function classifySseResult(res) {
    if (res.status === 0 && res.error_code === 1050) return 'completed';
    if (res.status === 200)                           return 'accepted';
    return 'failed';
}

export function connectSseStream(token, timeoutSec, readBody = false) {
    const timeoutMs = timeoutSec ? `${timeoutSec * 1000}ms` : '200s';
    return http.get(
        `${BASE_URL}/api/notifications/stream`,
        {
            headers: {
                Accept:        'text/event-stream',
                Authorization: `Bearer ${token}`,
            },
            responseType: readBody ? 'text' : 'none',
            timeout:      timeoutMs,
        }
    );
}

/**
 * SSE body에서 "event: notification" 라인 수 반환
 */
export function countNotificationEvents(body) {
    return (body.match(/event: notification\r?$/gm) || []).length;
}

/**
 * SSE body에서 "event: ping" 라인 수 반환
 */
export function countPingEvents(body) {
    return (body.match(/event: ping\r?$/gm) || []).length;
}

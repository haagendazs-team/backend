import http from 'k6/http';

// module API는 Gateway를 거치지 않고 notification 서비스 직접 호출
const NOTIFICATION_URL = __ENV.NOTIFICATION_URL || 'http://localhost:8081';

const HEADERS = { 'Content-Type': 'application/json' };

/**
 * 단건 알림 발행 — payment 결제 완료 이벤트
 * NotificationEnvelope: memberId(필수), payload, isDispatchType
 */
export function publishPayment(memberId, iter) {
    const envelope = {
        memberId,
        isDispatchType: 'IMMEDIATE',
        payload: JSON.stringify({ paymentId: iter, memberId, amount: 10000 }),
    };
    return http.post(
        `${NOTIFICATION_URL}/module/notifications/publish`,
        JSON.stringify(envelope),
        { headers: HEADERS }
    );
}

/**
 * 브로드캐스트 알림 발행 — ticket.opened 이벤트 (memberId 없음 = 전체 발송)
 */
export function publishBroadcast(iter) {
    const saleStartAt = `2099-01-${String((iter % 28) + 1).padStart(2, '0')}T10:00:00`;
    const envelope = {
        isDispatchType: 'IMMEDIATE',
        payload: JSON.stringify({ gameId: iter, saleStartAt }),
    };
    return http.post(
        `${NOTIFICATION_URL}/module/notifications/publish`,
        JSON.stringify(envelope),
        { headers: HEADERS }
    );
}

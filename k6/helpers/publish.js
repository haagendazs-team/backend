import http from 'k6/http';

// module API는 Gateway를 거치지 않고 notification 서비스 직접 호출
const NOTIFICATION_URL = __ENV.NOTIFICATION_URL || 'http://localhost:8081';

const HEADERS = { 'Content-Type': 'application/json' };

/**
 * 단건 알림 발행 — payment 결제 완료 이벤트
 * eventTypeCode는 서버 측 payload/DTO에서 처리
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


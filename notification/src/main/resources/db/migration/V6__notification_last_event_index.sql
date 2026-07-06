-- Last-Event-ID 재전송 쿼리용 복합 인덱스
-- V*.sql 수정 금지 (Flyway 불변 원칙)

CREATE INDEX IF NOT EXISTS idx_notifications_member_id_id
    ON notification.notifications(member_id, id);

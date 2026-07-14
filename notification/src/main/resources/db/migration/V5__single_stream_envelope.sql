-- 단일 Redis Stream(notif:stream:events) + envelope 방식으로 전환
-- V*.sql 수정 금지 (Flyway 불변 원칙)

ALTER TABLE notification.event_types
    DROP COLUMN IF EXISTS stream_key,
    DROP COLUMN IF EXISTS member_id_field,
    DROP COLUMN IF EXISTS scheduled_at_field,
    DROP COLUMN IF EXISTS scheduled_offset_minutes;

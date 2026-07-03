-- k6 알림 시드 데이터 정리
-- run.sh 가 -v vus=N -v seed_offset=M 으로 주입
--
-- 삭제 순서 (FK 의존 순):
--   notification_history → notifications → notification_events
--   → notification_settings → members

DELETE FROM notification_history
WHERE notification_id IN (
    SELECT id FROM notifications
    WHERE member_id BETWEEN :seed_offset AND :seed_offset + :vus - 1
);

DELETE FROM notifications
WHERE member_id BETWEEN :seed_offset AND :seed_offset + :vus - 1;

DELETE FROM notification_events
WHERE id IN (
    SELECT DISTINCT event_id FROM notifications
    WHERE member_id BETWEEN :seed_offset AND :seed_offset + :vus - 1
);

DELETE FROM notification_settings
WHERE member_id BETWEEN :seed_offset AND :seed_offset + :vus - 1;

DELETE FROM members
WHERE id BETWEEN :seed_offset AND :seed_offset + :vus - 1;

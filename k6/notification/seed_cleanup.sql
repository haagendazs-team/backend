-- k6 알림 시드 데이터 정리
-- run.sh 가 -v vus=N -v seed_offset=M 으로 주입

DELETE FROM notification.history
WHERE notification_id IN (
    SELECT id FROM notification.notifications
    WHERE member_id BETWEEN :seed_offset AND :seed_offset + :vus - 1
);

DELETE FROM notification.notifications
WHERE member_id BETWEEN :seed_offset AND :seed_offset + :vus - 1;

DELETE FROM notification.events
WHERE id NOT IN (
    SELECT DISTINCT event_id FROM notification.notifications
);

DELETE FROM notification.setting_entries
WHERE member_id BETWEEN :seed_offset AND :seed_offset + :vus - 1;

-- k6 알림 부하 테스트 시드 데이터
-- run.sh 가 -v vus=N -v seed_offset=M 으로 주입

-- setting_entries — 모든 이벤트 타입 알림 ON
INSERT INTO notification.setting_entries (member_id, event_type_code, is_enabled)
SELECT
    :seed_offset + i,
    et.code,
    TRUE
FROM generate_series(0, :vus - 1) AS i
CROSS JOIN notification.event_types et
WHERE et.is_enabled = TRUE
ON CONFLICT (member_id, event_type_code) DO NOTHING;

-- k6 알림 부하 테스트 시드 데이터
-- run.sh 가 -v vus=N -v seed_offset=M 으로 주입
--
-- 시나리오별 역할:
--   01-sse-connect  : VU 1~VUS → memberId = seed_offset + (VU % VUS)
--   02-sse-send     : /dev/events/publish 로 XADD (DB seed 불필요)
--   03-sse-receive  : receivers(VU 1~200) + triggers(memberId 순환)
-- ─────────────────────────────────────────────────────────────────

-- notification_settings — 전체 알림 ON
INSERT INTO notification_settings (member_id, ticket_open_alert, game_start_alert, payment_alert, chat_mention_alert, updated_at)
SELECT
    :seed_offset + i,
    TRUE,
    TRUE,
    TRUE,
    TRUE,
    NOW()
FROM generate_series(0, :vus - 1) AS i
ON CONFLICT (member_id) DO NOTHING;

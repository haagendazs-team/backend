-- k6 알림 부하 테스트 시드 데이터
-- run.sh 가 -v vus=N -v seed_offset=M 으로 주입
--
-- 멤버 범위: seed_offset ~ (seed_offset + vus - 1)  (기본: 10000 ~ 10499)
-- 기존 R__sample_data.sql 멤버(id 1~4)와 충돌하지 않는 범위 사용
--
-- 시나리오별 역할:
--   01-sse-connect  : VU 1~VUS → memberId = seed_offset + (VU % VUS)
--   02-sse-send     : /dev/events/publish 로 XADD (DB seed 불필요)
--   03-sse-receive  : receivers(VU 1~200) + triggers(memberId 순환)
-- ─────────────────────────────────────────────────────────────────

-- 1. Members (seed_offset ~ seed_offset + vus - 1)
INSERT INTO members (id, email, nickname, provider, provider_id, status, role, created_at, updated_at)
SELECT
    :seed_offset + i,
    'k6-notify-' || (:seed_offset + i) || '@test.com',
    'k6-notify-' || (:seed_offset + i),
    'KAKAO',
    'k6-notify-provider-' || (:seed_offset + i),
    'ACTIVE',
    'USER',
    NOW(),
    NOW()
FROM generate_series(0, :vus - 1) AS i
ON CONFLICT (id) DO NOTHING;

-- 2. notification_settings — 전체 알림 ON (기본값이지만 명시적으로 삽입)
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

-- 시퀀스 충돌 방지
SELECT setval(
    pg_get_serial_sequence('members', 'id'),
    GREATEST(
        (SELECT COALESCE(MAX(id), 0) FROM members),
        :seed_offset + :vus
    )
);

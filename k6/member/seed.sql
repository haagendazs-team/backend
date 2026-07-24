-- k6 member 부하 테스트 시드
-- run.sh 가 -v vus=N -v seed_offset=M 으로 주입
--
-- 비밀번호: PerfTest1! (BCrypt)
-- 이메일: k6-member-{seed_offset + i}@perf.test

INSERT INTO member.member (email, password, nickname, is_active, created_at)
SELECT
    'k6-member-' || (:seed_offset + i) || '@perf.test',
    '$2a$10$e7xH907i7P8z39R5R67cc.hWNPXzpyhMyYqK6iliVld1z4GxqA7r6',
    'k6user' || (:seed_offset + i),
    TRUE,
    NOW()
FROM generate_series(0, :vus - 1) AS i
ON CONFLICT (email) DO UPDATE
SET password = EXCLUDED.password,
    nickname = EXCLUDED.nickname,
    is_active = TRUE,
    updated_at = NOW();

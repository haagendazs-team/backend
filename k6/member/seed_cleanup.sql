-- k6 member 시드/테스트 데이터 정리
-- run.sh 가 -v vus=N -v seed_offset=M 으로 주입 (호환용, 패턴 삭제가 주)

-- 1) k6 계정이 속한 채널의 channel_member 제거
DELETE FROM member.channel_member
WHERE member_id IN (
    SELECT member_id FROM member.member
    WHERE email LIKE 'k6-member-%@perf.test'
       OR email LIKE 'k6-signup-%@perf.test'
);

-- 2) k6 계정이 OWNER인 워크스페이스의 채널 제거
DELETE FROM member.channel
WHERE workspace_id IN (
    SELECT wm.workspace_id
    FROM member.workspace_member wm
    JOIN member.member m ON m.member_id = wm.member_id
    WHERE wm.role = 'OWNER'
      AND (m.email LIKE 'k6-member-%@perf.test' OR m.email LIKE 'k6-signup-%@perf.test')
);

-- 3) k6 계정의 workspace_member 제거
DELETE FROM member.workspace_member
WHERE member_id IN (
    SELECT member_id FROM member.member
    WHERE email LIKE 'k6-member-%@perf.test'
       OR email LIKE 'k6-signup-%@perf.test'
);

-- 4) 멤버가 없는 워크스페이스 제거 (k6 테스트 잔여)
DELETE FROM member.workspace w
WHERE NOT EXISTS (
    SELECT 1 FROM member.workspace_member wm WHERE wm.workspace_id = w.workspace_id
);

-- 5) 토큰 · 회원 제거
DELETE FROM member.token
WHERE member_id IN (
    SELECT member_id FROM member.member
    WHERE email LIKE 'k6-member-%@perf.test'
       OR email LIKE 'k6-signup-%@perf.test'
);

DELETE FROM member.member
WHERE email LIKE 'k6-member-%@perf.test'
   OR email LIKE 'k6-signup-%@perf.test';

-- ============================================
-- haagendazs 성능테스트 더미 데이터
-- 회원 600명, 채널 50개, 채널당 메시지 100개
-- 참여 규칙: channelId = (memberId % 50) + 1  (k6 스크립트와 일치)
-- ============================================

-- 기존 데이터 정리 (재실행 대비)
TRUNCATE chat.message, chat.chat_participant, chat.chat_channel, chat.chat_member RESTART IDENTITY CASCADE;

-- 1. 회원 600명 (member_id 1 ~ 600)
INSERT INTO chat.chat_member (member_id, nickname, profile_image_url, synced_at)
SELECT
    gs,
    '테스트유저' || gs,
    NULL,
    NOW()
FROM generate_series(1, 600) AS gs;

-- 2. 채널 50개 (channel_id 1 ~ 50)
INSERT INTO chat.chat_channel (channel_id, workspace_id, channel_name, is_direct_message, dm_key, synced_at)
SELECT
    gs,
    1,
    '테스트채널' || gs,
    false,
    NULL,
    NOW()
FROM generate_series(1, 50) AS gs;

-- 3. 참여 관계: 각 회원을 (member_id % 50) + 1 채널에 배정
INSERT INTO chat.chat_participant (channel_id, member_id, last_read_message_id, joined_at)
SELECT
    (gs % 50) + 1,   -- k6의 채널 배정 규칙과 동일
    gs,
    NULL,
    NOW()
FROM generate_series(1, 600) AS gs;

-- 4. 각 채널에 메시지 100개씩 (커서 조회 테스트용)
INSERT INTO chat.message (channel_id, sender_id, content, created_at)
SELECT
    ch,
    ((ch - 1) * 12) + 1,   -- 해당 채널에 속한 회원 중 하나를 발신자로
    '더미 메시지 ' || msg_num,
    NOW() - (msg_num || ' seconds')::interval
FROM generate_series(1, 50) AS ch
CROSS JOIN generate_series(1, 100) AS msg_num;

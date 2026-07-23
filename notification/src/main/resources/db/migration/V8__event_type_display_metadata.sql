-- 이벤트 타입 UI 메타데이터 추가 — FE 배포 없이 새 토글 항목 노출 가능하도록
-- V*.sql 수정 금지 (Flyway 불변 원칙)

ALTER TABLE notification.event_types
    ADD COLUMN IF NOT EXISTS display_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS description  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS category     VARCHAR(50);

-- 기존 5개 이벤트 타입 메타데이터 백필
UPDATE notification.event_types SET
    display_name = '티켓 오픈 알림',
    description  = '티켓 판매가 시작될 때 알림을 받습니다.',
    category     = 'TICKET'
WHERE code = 'TICKET_OPEN';

UPDATE notification.event_types SET
    display_name = '경기 시작 알림',
    description  = '경기 시작 30분 전에 알림을 받습니다.',
    category     = 'TICKET'
WHERE code = 'GAME_START';

UPDATE notification.event_types SET
    display_name = '결제 완료 알림',
    description  = '결제가 완료되면 알림을 받습니다.',
    category     = 'PAYMENT'
WHERE code = 'PAYMENT_COMPLETED';

UPDATE notification.event_types SET
    display_name = '채팅 멘션 알림',
    description  = '채팅에서 나를 멘션하면 알림을 받습니다.',
    category     = 'CHAT'
WHERE code = 'CHAT_MENTION';

UPDATE notification.event_types SET
    display_name = '채널 초대 알림',
    description  = '채팅 채널에 초대되면 알림을 받습니다.',
    category     = 'CHAT'
WHERE code = 'CHAT_INVITED';

-- 초기 노출 대상 4개 — 나머지는 비활성화
UPDATE notification.event_types SET is_enabled = false
WHERE code IN ('TICKET_OPEN', 'GAME_START');

-- 워크스페이스 초대 신규 이벤트 타입 (producer 미연결 상태이므로 비활성화로 시드)
INSERT INTO notification.event_types
    (code, is_scheduled, is_single_target, is_enabled, display_name, description, category)
VALUES
    ('WORKSPACE_INVITED', false, true, false, '워크스페이스 초대 알림', '워크스페이스에 초대되면 알림을 받습니다.', 'WORKSPACE')
ON CONFLICT (code) DO NOTHING;

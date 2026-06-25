-- dynamic event type system
-- V*.sql 수정 금지 (Flyway 불변 원칙)

CREATE TABLE IF NOT EXISTS notification.event_types (
    code                     VARCHAR(50)  PRIMARY KEY,
    stream_key               VARCHAR(100) NOT NULL UNIQUE,
    is_scheduled             BOOLEAN      NOT NULL DEFAULT false,
    is_single_target         BOOLEAN      NOT NULL DEFAULT false,
    member_id_field          VARCHAR(100) NOT NULL DEFAULT 'memberId',
    scheduled_at_field       VARCHAR(100),
    scheduled_offset_minutes INT          NOT NULL DEFAULT 0,
    is_enabled               BOOLEAN      NOT NULL DEFAULT true,
    created_at               TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at               TIMESTAMP
);

CREATE TABLE IF NOT EXISTS notification.setting_entries (
    id              BIGINT      PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    member_id       BIGINT      NOT NULL,
    event_type_code VARCHAR(50) NOT NULL REFERENCES notification.event_types(code),
    is_enabled      BOOLEAN     NOT NULL DEFAULT true,
    UNIQUE(member_id, event_type_code)
);

CREATE INDEX IF NOT EXISTS idx_setting_entries_member ON notification.setting_entries(member_id);
CREATE INDEX IF NOT EXISTS idx_setting_entries_code   ON notification.setting_entries(event_type_code);

-- 기존 5개 이벤트 타입 시드 (ON CONFLICT DO NOTHING 필수 — 향후 seed도 동일 규칙)
INSERT INTO notification.event_types
    (code, stream_key, is_scheduled, is_single_target, member_id_field, scheduled_at_field, scheduled_offset_minutes)
VALUES
    ('TICKET_OPEN',       'notif:stream:ticket.opened',     true,  false, 'memberId', 'saleStartAt',  0),
    ('GAME_START',        'notif:stream:game.starting',     true,  false, 'memberId', 'gameStartAt',  30),
    ('PAYMENT_COMPLETED', 'notif:stream:payment.completed', false, true,  'memberId', null,           0),
    ('CHAT_MENTION',      'notif:stream:chat.mentioned',    false, true,  'memberId', null,           0),
    ('CHAT_INVITED',      'notif:stream:chat.invited',      false, true,  'memberId', null,           0)
ON CONFLICT (code) DO NOTHING;

-- 기존 settings → setting_entries 백필 (필수, broadcast 수신자 명부 보존)
INSERT INTO notification.setting_entries (member_id, event_type_code, is_enabled)
SELECT member_id, 'TICKET_OPEN', ticket_open_alert
FROM notification.settings
ON CONFLICT (member_id, event_type_code) DO NOTHING;

INSERT INTO notification.setting_entries (member_id, event_type_code, is_enabled)
SELECT member_id, 'GAME_START', game_start_alert
FROM notification.settings
ON CONFLICT (member_id, event_type_code) DO NOTHING;

INSERT INTO notification.setting_entries (member_id, event_type_code, is_enabled)
SELECT member_id, 'PAYMENT_COMPLETED', payment_alert
FROM notification.settings
ON CONFLICT (member_id, event_type_code) DO NOTHING;

INSERT INTO notification.setting_entries (member_id, event_type_code, is_enabled)
SELECT member_id, 'CHAT_MENTION', chat_mention_alert
FROM notification.settings
ON CONFLICT (member_id, event_type_code) DO NOTHING;

INSERT INTO notification.setting_entries (member_id, event_type_code, is_enabled)
SELECT member_id, 'CHAT_INVITED', chat_mention_alert
FROM notification.settings
ON CONFLICT (member_id, event_type_code) DO NOTHING;

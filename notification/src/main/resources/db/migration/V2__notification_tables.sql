-- notification 서비스 테이블 생성
-- 이 파일은 수정 금지 (Flyway 불변 원칙)

CREATE SEQUENCE IF NOT EXISTS notification.notifications_seq START 1 INCREMENT 50;
CREATE SEQUENCE IF NOT EXISTS notification.history_seq START 1 INCREMENT 50;

CREATE TABLE IF NOT EXISTS notification.notifications (
    id          BIGINT PRIMARY KEY DEFAULT nextval('notification.notifications_seq'),
    member_id   BIGINT       NOT NULL,
    event_id    BIGINT       NOT NULL,
    is_read     BOOLEAN      NOT NULL DEFAULT false,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    UNIQUE (event_id, member_id)
);

CREATE TABLE IF NOT EXISTS notification.events (
    id                BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    event_type        VARCHAR(50)  NOT NULL,
    payload           TEXT,
    status            VARCHAR(30)  NOT NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT now(),
    published_at      TIMESTAMP,
    scheduled_at      TIMESTAMP,
    stream_message_id VARCHAR(100) UNIQUE,
    retry_count       INT          NOT NULL DEFAULT 0,
    stuck_retry_count INT          NOT NULL DEFAULT 0,
    updated_at        TIMESTAMP
);

CREATE TABLE IF NOT EXISTS notification.settings (
    id                  BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    member_id           BIGINT  NOT NULL UNIQUE,
    ticket_open_alert   BOOLEAN NOT NULL DEFAULT true,
    game_start_alert    BOOLEAN NOT NULL DEFAULT true,
    payment_alert       BOOLEAN NOT NULL DEFAULT true,
    chat_mention_alert  BOOLEAN NOT NULL DEFAULT true,
    updated_at          TIMESTAMP
);

CREATE TABLE IF NOT EXISTS notification.channels (
    id              BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    member_id       BIGINT      NOT NULL,
    channel_type    VARCHAR(30) NOT NULL,
    channel_target  TEXT        NOT NULL,
    is_enabled      BOOLEAN     NOT NULL DEFAULT true,
    created_at      TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP,
    UNIQUE (member_id, channel_type)
);

CREATE TABLE IF NOT EXISTS notification.history (
    id              BIGINT PRIMARY KEY DEFAULT nextval('notification.history_seq'),
    notification_id BIGINT      NOT NULL,
    channel_type    VARCHAR(30) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    error_message   TEXT,
    created_at      TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_notifications_member_id ON notification.notifications(member_id);
CREATE INDEX IF NOT EXISTS idx_events_status ON notification.events(status);
CREATE INDEX IF NOT EXISTS idx_events_scheduled ON notification.events(scheduled_at) WHERE scheduled_at IS NOT NULL;

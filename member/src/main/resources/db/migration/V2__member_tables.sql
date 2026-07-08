-- member 서비스 테이블 생성
-- 이 파일은 수정 금지 (Flyway 불변 원칙)

CREATE TABLE IF NOT EXISTS member.member (
    member_id         BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    email             VARCHAR(255) NOT NULL UNIQUE,
    password          VARCHAR(255) NOT NULL,
    nickname          VARCHAR(100) NOT NULL,
    is_active         BOOLEAN      NOT NULL DEFAULT true,
    profile_image_url VARCHAR(500),
    created_at        TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP
);

CREATE TABLE IF NOT EXISTS member.token (
    id            BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    member_id     BIGINT       NOT NULL REFERENCES member.member(member_id) ON DELETE CASCADE,
    refresh_token VARCHAR(500) NOT NULL UNIQUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    expired_at    TIMESTAMP    NOT NULL
);

CREATE TABLE IF NOT EXISTS member.workspace (
    workspace_id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    name         VARCHAR(100) NOT NULL,
    icon_url     VARCHAR(500),
    subscription VARCHAR(50)  NOT NULL DEFAULT 'FREE',
    created_at   TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP
);

CREATE TABLE IF NOT EXISTS member.workspace_member (
    id           BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    workspace_id BIGINT       NOT NULL REFERENCES member.workspace(workspace_id) ON DELETE CASCADE,
    member_id    BIGINT       NOT NULL REFERENCES member.member(member_id) ON DELETE CASCADE,
    role         VARCHAR(30)  NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP,
    UNIQUE (workspace_id, member_id)
);

CREATE TABLE IF NOT EXISTS member.channel (
    channel_id        BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    workspace_id      BIGINT       NOT NULL REFERENCES member.workspace(workspace_id) ON DELETE CASCADE,
    name              VARCHAR(100) NOT NULL,
    is_direct_message BOOLEAN      NOT NULL DEFAULT false,
    created_at        TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP
);

CREATE TABLE IF NOT EXISTS member.channel_member (
    id         BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    channel_id BIGINT    NOT NULL REFERENCES member.channel(channel_id) ON DELETE CASCADE,
    member_id  BIGINT    NOT NULL REFERENCES member.member(member_id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (channel_id, member_id)
);

CREATE INDEX IF NOT EXISTS idx_token_member_id ON member.token(member_id);
CREATE INDEX IF NOT EXISTS idx_workspace_member_member_id ON member.workspace_member(member_id);
CREATE INDEX IF NOT EXISTS idx_workspace_member_workspace_id ON member.workspace_member(workspace_id);
CREATE INDEX IF NOT EXISTS idx_channel_workspace_id ON member.channel(workspace_id);
CREATE INDEX IF NOT EXISTS idx_channel_member_member_id ON member.channel_member(member_id);
CREATE INDEX IF NOT EXISTS idx_channel_member_channel_id ON member.channel_member(channel_id);

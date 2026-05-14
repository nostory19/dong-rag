-- 知识助手多轮会话与消息

CREATE TABLE IF NOT EXISTS assistant_conversations (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    group_id        BIGINT       NOT NULL,
    template_id     VARCHAR(64)  NOT NULL,
    title           VARCHAR(256),
    rolling_summary TEXT,
    slot_state_json TEXT,
    last_compressed_at TIMESTAMP,
    status          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_assistant_conv_user_group ON assistant_conversations (user_id, group_id);
CREATE INDEX IF NOT EXISTS idx_assistant_conv_updated ON assistant_conversations (updated_at DESC);

CREATE TABLE IF NOT EXISTS assistant_messages (
    id               BIGSERIAL PRIMARY KEY,
    conversation_id  BIGINT       NOT NULL REFERENCES assistant_conversations (id) ON DELETE CASCADE,
    role             VARCHAR(16)  NOT NULL,
    content          TEXT         NOT NULL,
    intent           VARCHAR(64),
    trace_id         VARCHAR(64),
    metadata_json    TEXT,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_assistant_msg_conv_created ON assistant_messages (conversation_id, created_at);

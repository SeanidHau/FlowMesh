-- V2: supplier 领域事件 Outbox。
-- 业务事务先写入此表，发布器成功收到 RocketMQ ACK 后再填写 published_at。
CREATE TABLE supplier_outbox_events (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL,
    topic VARCHAR(128) NOT NULL,
    tag VARCHAR(128) NOT NULL,
    payload JSONB NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    last_error VARCHAR(1000),
    published_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_supplier_outbox_pending
    ON supplier_outbox_events (created_at)
    WHERE published_at IS NULL;

-- V5: 保存死信重放与原始事件关联关系。
ALTER TABLE supplier_outbox_events
    ADD COLUMN original_event_id UUID;

CREATE INDEX idx_supplier_outbox_dead_lettered
    ON supplier_outbox_events (tenant_id, dead_lettered_at DESC)
    WHERE dead_lettered_at IS NOT NULL;

CREATE TABLE supplier_outbox_replay_audits (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    operator_user_id UUID NOT NULL,
    original_event_id UUID NOT NULL,
    replay_event_id UUID NOT NULL,
    reason VARCHAR(500) NOT NULL,
    source_status VARCHAR(32) NOT NULL,
    replay_status VARCHAR(32) NOT NULL,
    trace_id VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_supplier_replay_audits_tenant_created
    ON supplier_outbox_replay_audits (tenant_id, created_at DESC);

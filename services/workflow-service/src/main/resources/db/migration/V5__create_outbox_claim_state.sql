-- V5: 增加 Outbox 认领、退避和失败终态，避免多副本重复扫描同一批事件。
ALTER TABLE workflow_outbox_events
    ADD COLUMN next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    ADD COLUMN claimed_until TIMESTAMP WITH TIME ZONE,
    ADD COLUMN claim_token UUID,
    ADD COLUMN dead_lettered_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_workflow_outbox_available
    ON workflow_outbox_events (next_attempt_at, created_at)
    WHERE published_at IS NULL AND dead_lettered_at IS NULL;

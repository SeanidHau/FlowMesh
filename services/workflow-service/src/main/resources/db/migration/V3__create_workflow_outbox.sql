-- V3: 保存审批完成事件，供 supplier-service 可靠消费。
-- Outbox 由服务内部发布器读取，不作为面向租户查询的业务表启用 RLS。
CREATE TABLE workflow_outbox_events (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL,
    topic VARCHAR(128) NOT NULL,
    tag VARCHAR(128) NOT NULL,
    payload JSONB NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(1000),
    published_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_workflow_outbox_pending
    ON workflow_outbox_events (created_at)
    WHERE published_at IS NULL;

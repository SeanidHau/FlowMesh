-- V5: 认证安全审计事件。只记录身份、动作、结果和链路信息，不记录密码或原始 Token。
CREATE TABLE iam_audit_events (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    actor_user_id UUID,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id VARCHAR(128),
    result VARCHAR(32) NOT NULL,
    trace_id VARCHAR(128) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_iam_audit_events_result CHECK (result IN ('SUCCESS', 'FAILURE'))
);

CREATE INDEX idx_iam_audit_events_tenant_time
    ON iam_audit_events (tenant_id, occurred_at);

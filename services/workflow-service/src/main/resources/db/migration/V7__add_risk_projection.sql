-- V7: 保存风控结果事件的持久化幂等记录，并允许流程处于风控阶段。
ALTER TABLE workflow_instances
    DROP CONSTRAINT ck_workflow_instances_status,
    ADD CONSTRAINT ck_workflow_instances_status
        CHECK (status IN ('RISK_CHECKING', 'IN_PROGRESS', 'REJECTED', 'COMPLETED'));

CREATE TABLE workflow_risk_event_inbox (
    event_id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    application_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_workflow_risk_inbox_tenant_application
    ON workflow_risk_event_inbox (tenant_id, application_id);

ALTER TABLE workflow_risk_event_inbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE workflow_risk_event_inbox FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_workflow_risk_inbox ON workflow_risk_event_inbox
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::text)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::text);

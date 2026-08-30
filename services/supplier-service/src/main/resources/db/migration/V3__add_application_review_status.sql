-- V3: 增加审批中的供应商申请状态。
ALTER TABLE supplier_applications
    DROP CONSTRAINT IF EXISTS ck_supplier_applications_status;

ALTER TABLE supplier_applications
    ADD CONSTRAINT ck_supplier_applications_status
        CHECK (status IN ('SUBMITTED', 'IN_REVIEW', 'ENABLED'));

CREATE TABLE supplier_workflow_event_inbox (
    event_id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

ALTER TABLE supplier_workflow_event_inbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE supplier_workflow_event_inbox FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_supplier_workflow_event_inbox
    ON supplier_workflow_event_inbox
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::text);

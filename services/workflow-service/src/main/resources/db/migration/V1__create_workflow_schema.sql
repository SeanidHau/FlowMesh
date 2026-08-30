-- V1: workflow 服务的最小流程实例投影。
CREATE TABLE workflow_instances (
    id UUID PRIMARY KEY,
    application_id UUID NOT NULL UNIQUE,
    source_event_id UUID NOT NULL UNIQUE,
    tenant_id VARCHAR(64) NOT NULL,
    process_definition_key VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_workflow_instances_status CHECK (status IN ('STARTED'))
);

CREATE INDEX idx_workflow_instances_tenant ON workflow_instances (tenant_id);

-- V2: 为最小流程实例增加当前审批节点和乐观锁。
ALTER TABLE workflow_instances
    DROP CONSTRAINT ck_workflow_instances_status,
    ADD COLUMN current_task VARCHAR(64),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

UPDATE workflow_instances
SET status = 'IN_PROGRESS',
    current_task = 'PURCHASER_REVIEW'
WHERE current_task IS NULL;

ALTER TABLE workflow_instances
    ALTER COLUMN current_task SET NOT NULL;

ALTER TABLE workflow_instances
    ADD CONSTRAINT ck_workflow_instances_status
        CHECK (status IN ('IN_PROGRESS', 'COMPLETED'));

ALTER TABLE workflow_instances ENABLE ROW LEVEL SECURITY;
ALTER TABLE workflow_instances FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_workflow_instances ON workflow_instances
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::text);

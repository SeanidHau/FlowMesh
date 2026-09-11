-- V9：将审批节点拆为可独立认领的持久化任务，支持法务与财务并行会签。
CREATE TABLE workflow_tasks (
    id UUID PRIMARY KEY,
    workflow_instance_id UUID NOT NULL REFERENCES workflow_instances(id),
    tenant_id VARCHAR(64) NOT NULL,
    application_id UUID NOT NULL,
    task_key VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    completed_by UUID,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_workflow_tasks_instance_key UNIQUE (workflow_instance_id, task_key),
    CONSTRAINT ck_workflow_tasks_status CHECK (status IN ('PENDING', 'COMPLETED'))
);

CREATE INDEX idx_workflow_tasks_tenant_application
    ON workflow_tasks (tenant_id, application_id, status);

ALTER TABLE workflow_tasks ENABLE ROW LEVEL SECURITY;
ALTER TABLE workflow_tasks FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_workflow_tasks ON workflow_tasks
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::text)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::text);

-- 为升级前仍处于审批中的流程补建当前待办；历史已完成流程没有未完成任务需要回填。
INSERT INTO workflow_tasks (
    id, workflow_instance_id, tenant_id, application_id, task_key, status, created_at
)
SELECT gen_random_uuid(), id, tenant_id, application_id, current_task, 'PENDING', created_at
FROM workflow_instances
WHERE current_task IS NOT NULL
ON CONFLICT (workflow_instance_id, task_key) DO NOTHING;

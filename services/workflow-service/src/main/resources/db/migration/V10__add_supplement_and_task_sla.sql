-- V10：增加补件重审轮次、审批意见和任务 SLA 元数据。
ALTER TABLE workflow_instances
    ADD COLUMN applicant_user_id UUID,
    ADD COLUMN review_round INT NOT NULL DEFAULT 1;

ALTER TABLE workflow_instances
    DROP CONSTRAINT ck_workflow_instances_status,
    ADD CONSTRAINT ck_workflow_instances_status
        CHECK (status IN ('RISK_CHECKING', 'IN_PROGRESS', 'SUPPLEMENT_REQUIRED', 'REJECTED', 'COMPLETED'));

ALTER TABLE workflow_tasks
    DROP CONSTRAINT uk_workflow_tasks_instance_key,
    ADD COLUMN round_no INT NOT NULL DEFAULT 1,
    ADD COLUMN decision VARCHAR(32),
    ADD COLUMN comment VARCHAR(2000),
    ADD COLUMN reminder_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN reminded_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN due_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN escalated_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE workflow_tasks
    DROP CONSTRAINT ck_workflow_tasks_status,
    ADD CONSTRAINT ck_workflow_tasks_status
        CHECK (status IN ('PENDING', 'COMPLETED', 'RETURNED', 'ESCALATED', 'CANCELLED'));

ALTER TABLE workflow_tasks
    ADD CONSTRAINT uk_workflow_tasks_instance_round_key
        UNIQUE (workflow_instance_id, round_no, task_key);

CREATE INDEX idx_workflow_tasks_sla
    ON workflow_tasks (status, reminder_at, due_at)
    WHERE status = 'PENDING';

-- 为升级前仍处于审批中的历史待办补齐默认轮次和 SLA 时间。
UPDATE workflow_tasks
SET reminder_at = created_at + INTERVAL '20 hours',
    due_at = created_at + INTERVAL '24 hours'
WHERE status = 'PENDING' AND reminder_at IS NULL;

CREATE TABLE workflow_supplement_event_inbox (
    event_id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    application_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

ALTER TABLE workflow_supplement_event_inbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE workflow_supplement_event_inbox FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_workflow_supplement_inbox
    ON workflow_supplement_event_inbox
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::text)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::text);

-- SLA CronJob 使用独立的非超级用户维护角色跨租户扫描任务；普通业务账号不获得这些权限。
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'flowmesh_workflow_sla') THEN
        CREATE ROLE flowmesh_workflow_sla LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT BYPASSRLS;
    END IF;
END
$$;

GRANT USAGE ON SCHEMA workflow TO flowmesh_workflow_sla;
GRANT SELECT ON workflow.workflow_instances TO flowmesh_workflow_sla;
GRANT SELECT, INSERT, UPDATE ON workflow.workflow_tasks TO flowmesh_workflow_sla;
GRANT INSERT ON workflow.workflow_outbox_events TO flowmesh_workflow_sla;

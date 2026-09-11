#!/usr/bin/env bash
# 作用：以独立最小权限账号执行 Workflow 审批 SLA 催办和超时升级。
# 说明：该脚本不修改业务申请结论，只更新任务生命周期并写入 Workflow Outbox；
#       通知和运营处置仍由正常 RocketMQ 消费链路完成。

set -euo pipefail

: "${PGHOST:?PGHOST 必须设置}"
: "${PGPORT:=5432}"
: "${PGDATABASE:?PGDATABASE 必须设置}"
: "${PGUSER:?PGUSER 必须设置为 flowmesh_workflow_sla}"
: "${PGPASSWORD:?PGPASSWORD 必须设置}"

if [[ "$PGUSER" != "flowmesh_workflow_sla" ]]; then
  echo "PGUSER 必须是 flowmesh_workflow_sla，禁止使用业务账号执行 SLA 扫描。" >&2
  exit 1
fi

psql_args=(--host "$PGHOST" --port "$PGPORT" --username "$PGUSER" --dbname "$PGDATABASE" \
  --set ON_ERROR_STOP=1 --no-psqlrc)

# 先处理 20 小时催办。FOR UPDATE SKIP LOCKED 允许多个 CronJob 实例安全竞争。
psql "${psql_args[@]}" <<'SQL'
BEGIN;
WITH due AS (
    SELECT t.id, t.tenant_id, t.application_id, t.task_key, t.due_at,
           i.applicant_user_id, gen_random_uuid() AS event_id
    FROM workflow.workflow_tasks t
    JOIN workflow.workflow_instances i ON i.id = t.workflow_instance_id
    WHERE t.status = 'PENDING'
      AND t.reminder_at <= CURRENT_TIMESTAMP
      AND t.reminded_at IS NULL
    ORDER BY t.reminder_at
    LIMIT 100
    FOR UPDATE OF t SKIP LOCKED
), marked AS (
    UPDATE workflow.workflow_tasks t
    SET reminded_at = CURRENT_TIMESTAMP
    FROM due
    WHERE t.id = due.id
    RETURNING due.*
)
INSERT INTO workflow.workflow_outbox_events
    (id, tenant_id, aggregate_id, topic, tag, payload, created_at)
SELECT event_id, tenant_id, application_id, 'workflow-events',
       'WorkflowTaskSlaReminderRequested',
       jsonb_build_object(
           'eventId', event_id,
           'eventType', 'WorkflowTaskSlaReminderRequested',
           'schemaVersion', 1,
           'tenantId', tenant_id,
           'aggregateId', application_id,
           'occurredAt', CURRENT_TIMESTAMP,
           'traceId', 'workflow-sla-cronjob',
           'payload', jsonb_build_object(
               'applicantUserId', applicant_user_id,
               'taskKey', task_key,
               'dueAt', due_at
           )
       ),
       CURRENT_TIMESTAMP
FROM marked;
COMMIT;
SQL

# 再处理 24 小时超时。一个流程实例只创建一个运营升级任务，其他并行待办同时取消。
psql "${psql_args[@]}" <<'SQL'
BEGIN;
DO $$
DECLARE
    task_row RECORD;
    event_id UUID;
    escalation_task_id UUID;
BEGIN
    FOR task_row IN
        SELECT t.id, t.workflow_instance_id, t.tenant_id, t.application_id,
               t.round_no, t.task_key, t.due_at, i.applicant_user_id,
               i.version AS instance_version
        FROM workflow.workflow_tasks t
        JOIN workflow.workflow_instances i ON i.id = t.workflow_instance_id
        WHERE t.status = 'PENDING'
          AND t.due_at <= CURRENT_TIMESTAMP
          AND t.escalated_at IS NULL
          AND t.task_key IN ('PURCHASER_REVIEW', 'LEGAL_REVIEW', 'FINANCE_REVIEW')
        ORDER BY t.due_at
        LIMIT 100
        FOR UPDATE OF t, i SKIP LOCKED
    LOOP
        UPDATE workflow.workflow_tasks
        SET status = 'ESCALATED', escalated_at = CURRENT_TIMESTAMP
        WHERE id = task_row.id AND status = 'PENDING';

        IF NOT FOUND THEN
            CONTINUE;
        END IF;

        UPDATE workflow.workflow_tasks
        SET status = 'CANCELLED'
        WHERE workflow_instance_id = task_row.workflow_instance_id
          AND round_no = task_row.round_no
          AND status = 'PENDING';

        UPDATE workflow.workflow_instances
        SET current_task = 'OPERATIONS_ESCALATION', version = version + 1
        WHERE id = task_row.workflow_instance_id
          AND tenant_id = task_row.tenant_id
          AND status = 'IN_PROGRESS'
          AND version = task_row.instance_version;

        IF NOT FOUND THEN
            CONTINUE;
        END IF;

        escalation_task_id := gen_random_uuid();
        INSERT INTO workflow.workflow_tasks
            (id, workflow_instance_id, tenant_id, application_id, task_key, round_no,
             status, created_at)
        VALUES
            (escalation_task_id, task_row.workflow_instance_id, task_row.tenant_id,
             task_row.application_id, 'OPERATIONS_ESCALATION', task_row.round_no,
             'PENDING', CURRENT_TIMESTAMP);

        event_id := gen_random_uuid();
        INSERT INTO workflow.workflow_outbox_events
            (id, tenant_id, aggregate_id, topic, tag, payload, created_at)
        VALUES
            (event_id, task_row.tenant_id, task_row.application_id, 'workflow-events',
             'WorkflowTaskSlaEscalated',
             jsonb_build_object(
                 'eventId', event_id,
                 'eventType', 'WorkflowTaskSlaEscalated',
                 'schemaVersion', 1,
                 'tenantId', task_row.tenant_id,
                 'aggregateId', task_row.application_id,
                 'occurredAt', CURRENT_TIMESTAMP,
                 'traceId', 'workflow-sla-cronjob',
                 'payload', jsonb_build_object(
                     'applicantUserId', task_row.applicant_user_id,
                     'taskKey', task_row.task_key,
                     'dueAt', task_row.due_at,
                     'escalationTaskKey', 'OPERATIONS_ESCALATION'
                 )
             ),
             CURRENT_TIMESTAMP);
    END LOOP;
END
$$;
COMMIT;
SQL

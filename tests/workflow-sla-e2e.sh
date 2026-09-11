#!/usr/bin/env bash
# 作用：在临时 PostgreSQL 容器中验证 Workflow SLA 催办、并行审批超时升级和 Outbox 写入。
# 脚本只操作临时容器，退出时删除容器，不依赖本地业务数据库。
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTAINER="flowmesh-workflow-sla-e2e-$$"
PASSWORD="flowmesh-workflow-sla-e2e-password"

cleanup() {
  local exit_code=$?
  docker rm -f "${CONTAINER}" >/dev/null 2>&1 || true
  exit "${exit_code}"
}
trap cleanup EXIT

docker run --detach --name "${CONTAINER}" \
  --env POSTGRES_PASSWORD=postgres-password \
  --env POSTGRES_DB=flowmesh \
  --volume "${ROOT_DIR}:/workspace:ro" \
  postgres:16 >/dev/null

for attempt in $(seq 1 60); do
  if docker exec "${CONTAINER}" psql -v ON_ERROR_STOP=1 -U postgres -d flowmesh \
    -c 'SELECT 1' >/dev/null 2>&1; then
    break
  fi
  if [[ "${attempt}" -eq 60 ]]; then
    echo 'PostgreSQL Workflow SLA E2E 容器未能就绪。' >&2
    exit 1
  fi
  sleep 1
done

docker exec --interactive "${CONTAINER}" psql -v ON_ERROR_STOP=1 -U postgres -d flowmesh <<SQL
CREATE EXTENSION pgcrypto;
CREATE ROLE flowmesh_workflow_sla LOGIN PASSWORD '${PASSWORD}'
  NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT BYPASSRLS;
CREATE SCHEMA workflow;

CREATE TABLE workflow.workflow_instances (
  id UUID PRIMARY KEY,
  tenant_id TEXT NOT NULL,
  applicant_user_id UUID NOT NULL,
  status TEXT NOT NULL,
  current_task TEXT,
  version BIGINT NOT NULL
);

CREATE TABLE workflow.workflow_tasks (
  id UUID PRIMARY KEY,
  workflow_instance_id UUID NOT NULL,
  tenant_id TEXT NOT NULL,
  application_id UUID NOT NULL,
  task_key TEXT NOT NULL,
  round_no INTEGER NOT NULL,
  status TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  due_at TIMESTAMPTZ,
  reminder_at TIMESTAMPTZ,
  reminded_at TIMESTAMPTZ,
  escalated_at TIMESTAMPTZ
);

CREATE TABLE workflow.workflow_outbox_events (
  id UUID PRIMARY KEY,
  tenant_id TEXT NOT NULL,
  aggregate_id UUID NOT NULL,
  topic TEXT NOT NULL,
  tag TEXT NOT NULL,
  payload JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL
);

GRANT USAGE ON SCHEMA workflow TO flowmesh_workflow_sla;
GRANT SELECT, UPDATE ON workflow.workflow_instances TO flowmesh_workflow_sla;
GRANT SELECT, INSERT, UPDATE ON workflow.workflow_tasks, workflow.workflow_outbox_events
  TO flowmesh_workflow_sla;

INSERT INTO workflow.workflow_instances
  (id, tenant_id, applicant_user_id, status, current_task, version)
VALUES
  ('10000000-0000-0000-0000-000000000001', 'tenant-a',
   '20000000-0000-0000-0000-000000000001', 'IN_PROGRESS', 'PURCHASER_REVIEW', 0),
  ('10000000-0000-0000-0000-000000000002', 'tenant-a',
   '20000000-0000-0000-0000-000000000002', 'COMPLETED', NULL, 0);

INSERT INTO workflow.workflow_tasks
  (id, workflow_instance_id, tenant_id, application_id, task_key, round_no,
   status, created_at, due_at, reminder_at)
VALUES
  ('30000000-0000-0000-0000-000000000001',
   '10000000-0000-0000-0000-000000000001', 'tenant-a',
   '40000000-0000-0000-0000-000000000001', 'PURCHASER_REVIEW', 1, 'PENDING',
   now(), now() - interval '1 minute', now() - interval '1 minute'),
  ('30000000-0000-0000-0000-000000000002',
   '10000000-0000-0000-0000-000000000001', 'tenant-a',
   '40000000-0000-0000-0000-000000000001', 'LEGAL_REVIEW', 1, 'PENDING',
   now(), now() - interval '1 minute', now() - interval '1 minute'),
  ('30000000-0000-0000-0000-000000000003',
   '10000000-0000-0000-0000-000000000001', 'tenant-a',
   '40000000-0000-0000-0000-000000000001', 'FINANCE_REVIEW', 1, 'PENDING',
   now(), now() - interval '1 minute', now() - interval '1 minute'),
  ('30000000-0000-0000-0000-000000000004',
   '10000000-0000-0000-0000-000000000002', 'tenant-a',
   '40000000-0000-0000-0000-000000000002', 'PURCHASER_REVIEW', 1, 'PENDING',
   now(), now() - interval '1 minute', NULL);
SQL

docker exec \
  --env PGHOST=127.0.0.1 \
  --env PGPORT=5432 \
  --env PGDATABASE=flowmesh \
  --env PGUSER=flowmesh_workflow_sla \
  --env PGPASSWORD="${PASSWORD}" \
  "${CONTAINER}" bash /workspace/scripts/run-workflow-sla.sh

instance_state="$(docker exec "${CONTAINER}" psql -At -U postgres -d flowmesh \
  -c "SELECT current_task || ':' || version FROM workflow.workflow_instances WHERE id = '10000000-0000-0000-0000-000000000001'")"
task_states="$(docker exec "${CONTAINER}" psql -At -U postgres -d flowmesh \
  -c "SELECT string_agg(status, ',' ORDER BY status) FROM workflow.workflow_tasks")"
operations_count="$(docker exec "${CONTAINER}" psql -At -U postgres -d flowmesh \
  -c "SELECT count(*) FROM workflow.workflow_tasks WHERE task_key = 'OPERATIONS_ESCALATION' AND status = 'PENDING'")"
escalation_events="$(docker exec "${CONTAINER}" psql -At -U postgres -d flowmesh \
  -c "SELECT count(*) FROM workflow.workflow_outbox_events WHERE tag = 'WorkflowTaskSlaEscalated'")"
reminder_events="$(docker exec "${CONTAINER}" psql -At -U postgres -d flowmesh \
  -c "SELECT count(*) FROM workflow.workflow_outbox_events WHERE tag = 'WorkflowTaskSlaReminderRequested'")"
rolled_back_task="$(docker exec "${CONTAINER}" psql -At -U postgres -d flowmesh \
  -c "SELECT status || ':' || COALESCE(escalated_at::text, 'NULL') FROM workflow.workflow_tasks WHERE id = '30000000-0000-0000-0000-000000000004'")"

[[ "${instance_state}" == "OPERATIONS_ESCALATION:1" ]]
[[ "${task_states}" == "CANCELLED,CANCELLED,ESCALATED,PENDING,PENDING" ]]
[[ "${operations_count}" == "1" ]]
[[ "${escalation_events}" == "1" ]]
[[ "${reminder_events}" == "3" ]]
[[ "${rolled_back_task}" == "PENDING:NULL" ]]

echo 'Workflow SLA PostgreSQL E2E passed.'

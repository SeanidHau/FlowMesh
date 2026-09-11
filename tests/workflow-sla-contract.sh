#!/usr/bin/env bash
# 作用：离线校验审批 SLA 维护脚本、维护账号和 Helm CronJob 的关键安全约束。
set -Eeuo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
sla_script="${repo_root}/scripts/run-workflow-sla.sh"
cronjob="${repo_root}/infra/helm/flowmesh/templates/workflow-sla-cronjob.yaml"
migration="${repo_root}/services/workflow-service/src/main/resources/db/migration/V10__add_supplement_and_task_sla.sql"

grep -F 'flowmesh_workflow_sla' "${sla_script}" >/dev/null
grep -F 'FOR UPDATE OF t SKIP LOCKED' "${sla_script}" >/dev/null
grep -F 'WorkflowTaskSlaReminderRequested' "${sla_script}" >/dev/null
grep -F 'WorkflowTaskSlaEscalated' "${sla_script}" >/dev/null
grep -F 'PGUSER 必须是 flowmesh_workflow_sla' "${sla_script}" >/dev/null

grep -F 'BYPASSRLS' "${migration}" >/dev/null
grep -F 'GRANT SELECT ON workflow.workflow_instances' "${migration}" >/dev/null
grep -F 'GRANT SELECT, INSERT, UPDATE ON workflow.workflow_tasks' "${migration}" >/dev/null
grep -F 'kind: CronJob' "${cronjob}" >/dev/null
grep -F 'concurrencyPolicy: Forbid' "${cronjob}" >/dev/null
grep -F 'readOnlyRootFilesystem: true' "${cronjob}" >/dev/null
grep -F 'WORKFLOW_SLA_DB_PASSWORD' "${cronjob}" >/dev/null

if grep -Eq 'PGUSER.*postgres|PGUSER.*root' "${cronjob}"; then
  echo 'SLA CronJob 不得使用超级用户或 root 数据库账号。' >&2
  exit 1
fi

echo 'Workflow SLA contract passed.'

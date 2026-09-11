#!/usr/bin/env bash
# 作用：离线验证 Kubernetes 生产 smoke test 覆盖关键资源、Secret 和只读约束。
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
smoke_test="${repo_root}/tests/kubernetes-production-smoke.sh"

required_deployments=(
  gateway iam supplier workflow risk notification-audit
)
required_secret_keys=(
  JWT_SIGNING_KEY
  REDIS_PASSWORD
  IAM_DB_PASSWORD
  SUPPLIER_DB_PASSWORD
  WORKFLOW_DB_PASSWORD
  WORKFLOW_SLA_DB_PASSWORD
  RISK_DB_PASSWORD
  AUDIT_DB_PASSWORD
  NOTIFICATION_WEBHOOK_SIGNING_SECRET
  OBJECT_STORAGE_ACCESS_KEY
  OBJECT_STORAGE_SECRET_KEY
  ROCKETMQ_PRODUCER_ACCESS_KEY
  ROCKETMQ_PRODUCER_SECRET_KEY
  ROCKETMQ_CONSUMER_ACCESS_KEY
  ROCKETMQ_CONSUMER_SECRET_KEY
)

for deployment in "${required_deployments[@]}"; do
  grep -F -- "-flowmesh-${deployment}" "${smoke_test}" >/dev/null
done

for key in "${required_secret_keys[@]}"; do
  grep -F -- "${key}" "${smoke_test}" >/dev/null
done

for forbidden_command in 'kubectl apply' 'kubectl delete' 'kubectl patch' 'kubectl rollout restart' 'kubectl scale'; do
  if grep -vE '^[[:space:]]*#' "${smoke_test}" | grep -F -- "${forbidden_command}" >/dev/null; then
    echo "Kubernetes smoke test 不得执行写操作：${forbidden_command}" >&2
    exit 1
  fi
done

grep -F -- 'concurrencyPolicy=Forbid' "${smoke_test}" >/dev/null
grep -F -- 'FLOWMESH_PG_SSLMODE' "${smoke_test}" >/dev/null
grep -F -- 'FLOWMESH_BACKUP_POSTGRES_USER' "${smoke_test}" >/dev/null
grep -F -- 'workflow_sla_cronjob' "${smoke_test}" >/dev/null
grep -F -- 'WORKFLOW_SLA_DB_PASSWORD' "${smoke_test}" >/dev/null
grep -F -- 'automountServiceAccountToken' "${smoke_test}" >/dev/null
grep -F -- 'FLOWMESH_NOTIFICATION_DELIVERY_ENABLED' "${smoke_test}" >/dev/null
grep -F -- 'FLOWMESH_NOTIFICATION_WEBHOOK_URL' "${smoke_test}" >/dev/null

echo 'Kubernetes production smoke contract passed.'

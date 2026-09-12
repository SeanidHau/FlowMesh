#!/usr/bin/env bash
# 作用：防止生产环境实施手册遗漏发布工作流中的关键入口、配置项和验收证据。

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
document="${repo_root}/docs/production-environment.md"

test -s "${document}"

required_markers=(
  'scripts/deploy-production.sh'
  'scripts/run-production-acceptance.sh'
  'scripts/validate-production-dependencies.sh'
  'scripts/validate-backup-role.sh'
  'scripts/validate-retention-role.sh'
  'scripts/prepare-postgres-migration-roles.sh'
  'scripts/create-production-evidence-manifest.sh'
  'scripts/validate-production-evidence.sh'
  'Production deploy'
  'Production acceptance'
  'helm upgrade --install --atomic --wait'
  'kubernetes-smoke.md'
  'dependency-ha.md'
  'runtime-observability.md'
  'service-recovery.md'
  'backup-restore.md'
  'load-test.md'
  'security-regression.md'
  'alert-routing.md'
)

for marker in "${required_markers[@]}"; do
  grep -F -- "${marker}" "${document}" >/dev/null || {
    printf '生产环境实施手册缺少关键内容：%s\n' "${marker}" >&2
    exit 1
  }
done

required_variables=(
  FLOWMESH_RUNTIME_SECRET_NAME
  FLOWMESH_MIGRATION_SECRET_NAME
  FLOWMESH_BACKUP_SECRET_NAME
  FLOWMESH_RETENTION_SECRET_NAME
  FLOWMESH_INGRESS_HOST
  FLOWMESH_INGRESS_TLS_SECRET_NAME
  FLOWMESH_POSTGRES_HOST
  FLOWMESH_POSTGRES_CA_SECRET_NAME
  FLOWMESH_REDIS_HOST
  FLOWMESH_ROCKETMQ_NAMESRV_ADDR
  FLOWMESH_OBJECT_STORAGE_ENDPOINT
  FLOWMESH_CLAMAV_HOST
  FLOWMESH_BACKUP_POSTGRES_HOST
  FLOWMESH_BACKUP_POSTGRES_USER
  FLOWMESH_BACKUP_S3_URI
  FLOWMESH_ALERTMANAGER_CONFIG_SECRET_NAME
  FLOWMESH_NOTIFICATION_WEBHOOK_URL
  FLOWMESH_NETWORK_POLICY_EXTERNAL_CIDRS
  FLOWMESH_PG_PASSWORD
  FLOWMESH_BACKUP_DB_PASSWORD
  FLOWMESH_RETENTION_DB_PASSWORD
  FLOWMESH_REDIS_PASSWORD
)

for variable in "${required_variables[@]}"; do
  grep -F -- "${variable}" "${document}" >/dev/null || {
    printf '生产环境实施手册缺少变量：%s\n' "${variable}" >&2
    exit 1
  }
done

if grep -n -E 'BEGIN (RSA|OPENSSH|EC|PRIVATE) KEY|Authorization:[[:space:]]*Bearer|PASSWORD=[^<`]' "${document}" >/dev/null; then
  echo '生产环境实施手册疑似包含凭据内容。' >&2
  exit 1
fi

echo 'Production environment documentation contract passed.'

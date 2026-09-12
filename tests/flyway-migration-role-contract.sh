#!/usr/bin/env bash
# 作用：验证已有数据库的 Flyway 迁移账号切换脚本保留最小权限和通知投递安全边界。

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
script="${repo_root}/scripts/prepare-postgres-migration-roles.sh"

bash -n "${script}"
for role in iam supplier workflow risk audit; do
  grep -F -- "flowmesh_${role}_migrator" "${script}" >/dev/null
done
grep -F -- 'NOSUPERUSER' "${script}" >/dev/null
grep -F -- 'REVOKE CREATE ON SCHEMA' "${script}" >/dev/null
grep -F -- 'notification_deliveries' "${script}" >/dev/null
grep -F -- "flowmesh_audit_delivery'" "${script}" >/dev/null
grep -F -- 'FLOWMESH_ADMIN_PGURI' "${script}" >/dev/null

echo 'Flyway migration role contract passed.'

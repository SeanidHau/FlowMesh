#!/usr/bin/env bash
# 作用：离线验证备份角色预检的参数校验、只读边界和最小权限检查标记。

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
script="${repo_root}/scripts/validate-backup-role.sh"

bash -n "${script}"
if FLOWMESH_BACKUP_DB_PASSWORD=test FLOWMESH_PG_SSLMODE=invalid "${script}" >/dev/null 2>&1; then
  echo '备份角色预检应拒绝非法 PostgreSQL SSL 模式。' >&2
  exit 1
fi

for marker in \
  'rolsuper' \
  'rolbypassrls' \
  'has_schema_privilege' \
  "has_table_privilege(backup_role_name, qualified_table, 'SELECT')" \
  "has_table_privilege(backup_role_name, qualified_table, 'INSERT')" \
  "has_table_privilege(backup_role_name, qualified_table, 'UPDATE')"; do
  grep -F -- "${marker}" "${script}" >/dev/null || {
    printf '备份角色预检缺少关键检查：%s\n' "${marker}" >&2
    exit 1
  }
done

if grep -vE '^[[:space:]]*#' "${script}" | grep -E 'kubectl[[:space:]]+(apply|delete|patch|rollout|scale)|docker compose[[:space:]]+(up|down|start|stop)' >/dev/null; then
  echo '备份角色预检不得执行集群或容器写操作。' >&2
  exit 1
fi

echo 'Backup role contract passed.'

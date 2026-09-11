#!/usr/bin/env bash
# 作用：离线验证生命周期角色预检的连接门禁、最小权限 SQL 和只读边界。
set -Eeuo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
temp_root="$(mktemp -d "${TMPDIR:-/tmp}/flowmesh-retention-role-contract.XXXXXX")"
fake_bin="${temp_root}/bin"
sql_log="${temp_root}/retention-role.sql"
mkdir -p "${fake_bin}"
trap 'rm -rf -- "${temp_root}"' EXIT

cat >"${fake_bin}/psql" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail
cat >"${PSQL_SQL_LOG:?}"
EOF
chmod +x "${fake_bin}/psql"

if PATH="${fake_bin}:${PATH}" \
  FLOWMESH_PG_HOST=postgres.internal \
  FLOWMESH_RETENTION_DB_PASSWORD= \
  "${repo_root}/scripts/validate-retention-role.sh" >/dev/null 2>&1; then
  echo '生命周期角色预检缺少密码时不应继续。' >&2
  exit 1
fi

PATH="${fake_bin}:${PATH}" \
FLOWMESH_PG_HOST=postgres.internal \
FLOWMESH_RETENTION_DB_PASSWORD=test-password \
FLOWMESH_PG_SSLMODE=require \
PSQL_SQL_LOG="${sql_log}" \
  "${repo_root}/scripts/validate-retention-role.sh" >/dev/null

for table in \
  supplier.supplier_outbox_events \
  supplier.supplier_outbox_replay_audits \
  supplier.supplier_workflow_event_inbox \
  supplier.supplier_idempotency_keys \
  workflow.workflow_outbox_events \
  workflow.workflow_outbox_replay_audits \
  workflow.workflow_risk_event_inbox \
  risk.risk_outbox_events \
  audit.audit_event_inbox; do
  grep -F "${table#*.}" "${sql_log}" >/dev/null || {
    echo "生命周期角色预检 SQL 缺少白名单表：${table}" >&2
    exit 1
  }
done

grep -F 'rolbypassrls' "${sql_log}" >/dev/null
grep -F 'has_table_privilege' "${sql_log}" >/dev/null
grep -F 'has_column_privilege' "${sql_log}" >/dev/null
grep -F 'pg_auth_members' "${sql_log}" >/dev/null
if grep -Eq '^[[:space:]]*(INSERT|UPDATE|DELETE|TRUNCATE|ALTER|DROP|CREATE)[[:space:]]' "${sql_log}"; then
  echo '生命周期角色预检不应包含数据变更或 DDL。' >&2
  exit 1
fi

echo 'Retention role contract passed.'

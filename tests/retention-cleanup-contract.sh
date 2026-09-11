#!/usr/bin/env bash
# 作用：离线验证生命周期清理脚本的确认门禁、维护角色约束、SQL 表白名单和批量锁语义。
set -Eeuo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
temp_root="$(mktemp -d "${TMPDIR:-/tmp}/flowmesh-retention-contract.XXXXXX")"
fake_bin="${temp_root}/bin"
sql_log="${temp_root}/retention.sql"
mkdir -p "${fake_bin}"
trap 'rm -rf -- "${temp_root}"' EXIT

cat >"${fake_bin}/psql" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail

if printf '%s\n' "$*" | grep -q 'pg_roles'; then
  printf '%s\n' "${FAKE_PSQL_ROLE_STATUS:-ok}"
  exit 0
fi
if printf '%s\n' "$*" | grep -q 'pg_try_advisory_lock'; then
  printf '%s\n' "${FAKE_PSQL_LOCK_STATUS:-t}"
  exit 0
fi
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --file)
      cp -- "$2" "${PSQL_SQL_LOG:?}"
      exit 0
      ;;
    --file=*)
      cp -- "${1#--file=}" "${PSQL_SQL_LOG:?}"
      exit 0
      ;;
  esac
  shift
done
exit 0
EOF
chmod +x "${fake_bin}/psql"

if FLOWMESH_RETENTION_CONFIRM=NO FLOWMESH_RETENTION_DB_PASSWORD=test \
  PATH="${fake_bin}:${PATH}" "${repo_root}/scripts/cleanup-flowmesh-retention.sh" >/dev/null 2>&1; then
  echo '缺少精确确认值时不应执行生命周期清理。' >&2
  exit 1
fi

PATH="${fake_bin}:${PATH}" \
FLOWMESH_RETENTION_CONFIRM=YES \
FLOWMESH_RETENTION_DB_PASSWORD=test \
FLOWMESH_PG_SSLMODE=disable \
PSQL_SQL_LOG="${sql_log}" \
  "${repo_root}/scripts/cleanup-flowmesh-retention.sh" >/dev/null

for table in \
  supplier.supplier_outbox_events \
  supplier.supplier_outbox_replay_audits \
  supplier.supplier_workflow_event_inbox \
  supplier.supplier_idempotency_keys \
  workflow.workflow_outbox_events \
  workflow.workflow_outbox_replay_audits \
  workflow.workflow_risk_event_inbox \
  risk.risk_outbox_events \
  audit.audit_event_inbox \
  audit.notification_deliveries; do
  grep -F "${table}" "${sql_log}" >/dev/null || {
    echo "清理 SQL 缺少表白名单项：${table}" >&2
    exit 1
  }
done
grep -F 'FOR UPDATE SKIP LOCKED' "${sql_log}" >/dev/null
grep -F 'dead_lettered_at' "${sql_log}" >/dev/null
grep -F 'audit.notification_deliveries' "${sql_log}" >/dev/null
grep -F "current_setting('flowmesh.outbox_retention_days')::int" "${sql_log}" >/dev/null
if grep -Eq 'DELETE FROM (audit\.audit_events|audit\.notifications|supplier\.supplier_applications)' "${sql_log}"; then
  echo '生命周期清理不应删除审批审计、通知或业务申请数据。' >&2
  exit 1
fi

if PATH="${fake_bin}:${PATH}" \
  FLOWMESH_RETENTION_CONFIRM=YES \
  FLOWMESH_RETENTION_DB_PASSWORD=test \
  FLOWMESH_RETENTION_BATCH_SIZE=0 \
  "${repo_root}/scripts/cleanup-flowmesh-retention.sh" >/dev/null 2>&1; then
  echo '非正批量大小未被拒绝。' >&2
  exit 1
fi

set +e
output="$(PATH="${fake_bin}:${PATH}" \
  FAKE_PSQL_ROLE_STATUS=reject \
  FLOWMESH_RETENTION_CONFIRM=YES \
  FLOWMESH_RETENTION_DB_PASSWORD=test \
  "${repo_root}/scripts/cleanup-flowmesh-retention.sh" 2>&1)"
status=$?
set -e
[[ "${status}" -eq 77 ]] || {
  echo "维护角色校验应返回 77，实际为 ${status}。" >&2
  exit 1
}
[[ "${output}" == *'专用维护角色'* ]] || {
  echo '维护角色校验失败时未输出明确错误。' >&2
  exit 1
}

echo 'Retention cleanup contract passed.'

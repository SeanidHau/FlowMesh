#!/usr/bin/env bash
# 作用：离线验证 PostgreSQL 恢复脚本使用 TLS/连接超时配置，并拒绝无效参数。

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
temporary_directory="$(mktemp -d "${TMPDIR:-/tmp}/flowmesh-postgres-restore-contract.XXXXXX")"
fake_bin="${temporary_directory}/bin"
backup_directory="${temporary_directory}/backup"
restore_log="${temporary_directory}/restore.log"
mkdir -p "${fake_bin}" "${backup_directory}"
trap 'rm -rf -- "${temporary_directory}"' EXIT

printf 'fake dump\n' > "${backup_directory}/flowmesh.dump"
printf 'fake globals\n' > "${backup_directory}/globals.sql"
if command -v sha256sum >/dev/null 2>&1; then
  (cd "${backup_directory}" && sha256sum flowmesh.dump globals.sql > checksums.sha256)
else
  (cd "${backup_directory}" && shasum -a 256 flowmesh.dump globals.sql > checksums.sha256)
fi

cat > "${fake_bin}/pg_restore" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s|%s\n' "${PGSSLMODE:-}" "${PGCONNECT_TIMEOUT:-}" >> "${RESTORE_LOG:?}"
if [[ "${1:-}" == '--list' ]]; then
  exit 0
fi
EOF
chmod +x "${fake_bin}/pg_restore"

PATH="${fake_bin}:${PATH}" \
RESTORE_LOG="${restore_log}" \
FLOWMESH_CONFIRM_RESTORE=YES \
FLOWMESH_PG_PASSWORD=test-only \
FLOWMESH_PG_SSLMODE=verify-full \
FLOWMESH_PG_CONNECT_TIMEOUT_SECONDS=9 \
  "${repo_root}/scripts/restore-postgres.sh" "${backup_directory}" >/dev/null

grep -Fx -- 'verify-full|9' "${restore_log}" >/dev/null
[[ "$(wc -l < "${restore_log}")" -eq 2 ]]

if PATH="${fake_bin}:${PATH}" \
  RESTORE_LOG="${restore_log}" \
  FLOWMESH_CONFIRM_RESTORE=YES \
  FLOWMESH_PG_PASSWORD=test-only \
  FLOWMESH_PG_SSLMODE=invalid \
    "${repo_root}/scripts/restore-postgres.sh" "${backup_directory}" >/dev/null 2>&1; then
  echo '无效的 PostgreSQL SSL 模式未被恢复脚本拒绝。' >&2
  exit 1
fi
[[ "$(wc -l < "${restore_log}")" -eq 2 ]]

if PATH="${fake_bin}:${PATH}" \
  RESTORE_LOG="${restore_log}" \
  FLOWMESH_CONFIRM_RESTORE=YES \
  FLOWMESH_PG_PASSWORD=test-only \
  FLOWMESH_PG_CONNECT_TIMEOUT_SECONDS=0 \
    "${repo_root}/scripts/restore-postgres.sh" "${backup_directory}" >/dev/null 2>&1; then
  echo '无效的 PostgreSQL 连接超时未被恢复脚本拒绝。' >&2
  exit 1
fi
[[ "$(wc -l < "${restore_log}")" -eq 2 ]]

echo 'PostgreSQL restore contract passed.'

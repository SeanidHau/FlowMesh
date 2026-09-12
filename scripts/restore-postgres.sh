#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/postgres-tls.sh"

# 作用：将 custom-format PostgreSQL 备份恢复到目标数据库。
# 为避免误覆盖线上数据，恢复必须显式设置 FLOWMESH_CONFIRM_RESTORE=YES。

: "${FLOWMESH_CONFIRM_RESTORE:?恢复前必须设置 FLOWMESH_CONFIRM_RESTORE=YES}"
if [[ "${FLOWMESH_CONFIRM_RESTORE}" != "YES" ]]; then
  printf '拒绝恢复：FLOWMESH_CONFIRM_RESTORE 必须精确为 YES。\n' >&2
  exit 1
fi

backup_dir="${1:-}"
if [[ -z "${backup_dir}" || ! -f "${backup_dir}/flowmesh.dump" || ! -f "${backup_dir}/checksums.sha256" ]]; then
  printf '用法：FLOWMESH_CONFIRM_RESTORE=YES FLOWMESH_PG_PASSWORD=... %s <backup-dir>\n' "$0" >&2
  exit 2
fi

ssl_mode="${FLOWMESH_PG_SSLMODE:-disable}"
case "${ssl_mode}" in
  disable|allow|prefer|require|verify-ca|verify-full)
    ;;
  *)
    printf '不支持的 FLOWMESH_PG_SSLMODE：%s\n' "${ssl_mode}" >&2
    exit 1
    ;;
esac
flowmesh_require_postgres_ca "${ssl_mode}" "${FLOWMESH_PG_SSLROOTCERT:-}"

connect_timeout="${FLOWMESH_PG_CONNECT_TIMEOUT_SECONDS:-5}"
if [[ ! "${connect_timeout}" =~ ^[1-9][0-9]*$ ]]; then
  printf 'FLOWMESH_PG_CONNECT_TIMEOUT_SECONDS 必须是正整数：%s\n' "${connect_timeout}" >&2
  exit 1
fi

# 作用：恢复前先验证备份清单，避免将损坏归档写入目标数据库。
"$(dirname "$0")/verify-postgres-backup.sh" "${backup_dir}"

: "${FLOWMESH_PG_PASSWORD:?请设置 FLOWMESH_PG_PASSWORD}"
database="${FLOWMESH_PG_DATABASE:-flowmesh}"
host="${FLOWMESH_PG_HOST:-localhost}"
port="${FLOWMESH_PG_PORT:-5432}"
user="${FLOWMESH_PG_USER:-flowmesh}"
export PGPASSWORD="${FLOWMESH_PG_PASSWORD}"
export PGCONNECT_TIMEOUT="${connect_timeout}"
export PGSSLMODE="${ssl_mode}"
if [[ -n "${FLOWMESH_PG_SSLROOTCERT:-}" ]]; then
  export PGSSLROOTCERT="${FLOWMESH_PG_SSLROOTCERT}"
fi
cleanup_client_environment() {
  unset PGPASSWORD PGCONNECT_TIMEOUT PGSSLMODE PGSSLROOTCERT
}
trap cleanup_client_environment EXIT

pg_restore \
  --exit-on-error \
  --no-owner \
  --no-privileges \
  --dbname="${database}" \
  --host="${host}" \
  --port="${port}" \
  --username="${user}" \
  "${backup_dir}/flowmesh.dump"

# 作用：恢复完成或失败后清理 PostgreSQL 客户端环境变量，避免凭据和连接参数泄漏到后续命令。
cleanup_client_environment
trap - EXIT
printf 'PostgreSQL 数据已恢复。角色定义请由数据库管理员审核后执行 globals.sql。\n'

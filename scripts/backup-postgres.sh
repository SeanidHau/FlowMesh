#!/usr/bin/env bash
set -euo pipefail

# 作用：将 FlowMesh PostgreSQL 数据库和角色定义导出为可验证的备份目录。
# 密码只通过 FLOWMESH_PG_PASSWORD 注入，不出现在命令行参数中。

: "${FLOWMESH_PG_PASSWORD:?请设置 FLOWMESH_PG_PASSWORD}"

backup_root="${FLOWMESH_BACKUP_ROOT:-./backups/postgres}"
database="${FLOWMESH_PG_DATABASE:-flowmesh}"
host="${FLOWMESH_PG_HOST:-localhost}"
port="${FLOWMESH_PG_PORT:-5432}"
user="${FLOWMESH_PG_USER:-flowmesh}"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
backup_dir="${backup_root}/${timestamp}"

mkdir -p "${backup_dir}"
chmod 700 "${backup_dir}"
export PGPASSWORD="${FLOWMESH_PG_PASSWORD}"

pg_dump \
  --format=custom \
  --no-owner \
  --no-privileges \
  --file="${backup_dir}/flowmesh.dump" \
  --host="${host}" \
  --port="${port}" \
  --username="${user}" \
  "${database}"

pg_dumpall \
  --globals-only \
  --host="${host}" \
  --port="${port}" \
  --username="${user}" \
  > "${backup_dir}/globals.sql"

if command -v sha256sum >/dev/null 2>&1; then
  (cd "${backup_dir}" && sha256sum flowmesh.dump globals.sql > checksums.sha256)
else
  (cd "${backup_dir}" && shasum -a 256 flowmesh.dump globals.sql > checksums.sha256)
fi

chmod 600 "${backup_dir}/flowmesh.dump" "${backup_dir}/globals.sql" "${backup_dir}/checksums.sha256"
unset PGPASSWORD

printf 'PostgreSQL 备份已创建：%s\n' "${backup_dir}"

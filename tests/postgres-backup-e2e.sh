#!/usr/bin/env bash

# 作用：在临时 PostgreSQL 容器中验证备份、离线校验和隔离数据库恢复闭环。
# 脚本不使用本地数据库，也不会写入仓库；退出时删除测试容器和临时目录。

set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTAINER="flowmesh-postgres-backup-e2e-$$"
BACKUP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/flowmesh-postgres-backup.XXXXXX")"
POSTGRES_PASSWORD="flowmesh-backup-e2e-password"

cleanup() {
  local status=$?
  docker rm -f "${CONTAINER}" >/dev/null 2>&1 || true
  rm -rf -- "${BACKUP_ROOT}"
  exit "${status}"
}
trap cleanup EXIT

docker run --detach --name "${CONTAINER}" \
  --env POSTGRES_PASSWORD="${POSTGRES_PASSWORD}" \
  --env POSTGRES_DB=flowmesh \
  --volume "${ROOT_DIR}:/workspace:ro" \
  --volume "${BACKUP_ROOT}:/backup" \
  postgres:16 >/dev/null

for attempt in $(seq 1 60); do
  if docker exec "${CONTAINER}" psql -v ON_ERROR_STOP=1 -U postgres -d flowmesh \
    -c 'SELECT 1' >/dev/null 2>&1; then
    break
  fi
  if [[ "${attempt}" -eq 60 ]]; then
    echo "PostgreSQL 测试容器未能就绪。" >&2
    exit 1
  fi
  sleep 1
done

docker exec --interactive "${CONTAINER}" psql -v ON_ERROR_STOP=1 -U postgres -d flowmesh <<'SQL'
CREATE TABLE backup_probe (id integer PRIMARY KEY, value text NOT NULL);
INSERT INTO backup_probe (id, value) VALUES (1, 'backup-e2e');
SQL

docker exec \
  --env FLOWMESH_PG_PASSWORD="${POSTGRES_PASSWORD}" \
  --env FLOWMESH_PG_DATABASE=flowmesh \
  --env FLOWMESH_PG_HOST=127.0.0.1 \
  --env FLOWMESH_PG_USER=postgres \
  --env FLOWMESH_BACKUP_ROOT=/backup \
  "${CONTAINER}" /workspace/scripts/backup-postgres.sh

backup_dir="$(find "${BACKUP_ROOT}" -mindepth 1 -maxdepth 1 -type d ! -name '.*' -print -quit)"
if [[ -z "${backup_dir}" ]]; then
  echo "备份脚本没有生成归档目录。" >&2
  exit 1
fi
backup_name="$(basename "${backup_dir}")"

docker exec "${CONTAINER}" \
  /workspace/scripts/verify-postgres-backup.sh "/backup/${backup_name}"

if grep -Eiq '(^|[[:space:]])(CREATE|ALTER)[[:space:]]+ROLE[^;]*PASSWORD' \
  "${backup_dir}/globals.sql"; then
  echo "全局对象备份不应包含角色密码。" >&2
  exit 1
fi

docker exec "${CONTAINER}" psql -v ON_ERROR_STOP=1 -U postgres -d postgres \
  -c 'CREATE DATABASE flowmesh_restore' >/dev/null

docker exec \
  --env FLOWMESH_CONFIRM_RESTORE=YES \
  --env FLOWMESH_PG_PASSWORD="${POSTGRES_PASSWORD}" \
  --env FLOWMESH_PG_DATABASE=flowmesh_restore \
  --env FLOWMESH_PG_HOST=127.0.0.1 \
  --env FLOWMESH_PG_USER=postgres \
  "${CONTAINER}" /workspace/scripts/restore-postgres.sh "/backup/${backup_name}"

restored_value="$(docker exec "${CONTAINER}" psql -At -U postgres -d flowmesh_restore \
  -c 'SELECT value FROM backup_probe WHERE id = 1')"
if [[ "${restored_value}" != "backup-e2e" ]]; then
  echo "恢复后的数据校验失败：${restored_value}" >&2
  exit 1
fi

echo "PostgreSQL 备份恢复 E2E 通过：${backup_name}"

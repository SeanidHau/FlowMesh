#!/usr/bin/env bash
# 作用：为备份容器设置临时工作目录，并执行统一的 PostgreSQL 备份脚本。
set -euo pipefail

export FLOWMESH_BACKUP_ROOT="${FLOWMESH_BACKUP_ROOT:-/backup}"
export HOME="${HOME:-/tmp}"
export AWS_CONFIG_FILE="${AWS_CONFIG_FILE:-/tmp/aws/config}"
export AWS_SHARED_CREDENTIALS_FILE="${AWS_SHARED_CREDENTIALS_FILE:-/tmp/aws/credentials}"

exec /opt/flowmesh/scripts/backup-postgres.sh

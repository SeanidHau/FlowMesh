#!/usr/bin/env bash
# 作用：先只读核验维护账号权限，再执行 PostgreSQL 保留策略脚本。
set -euo pipefail

export FLOWMESH_RETENTION_CONFIRM="${FLOWMESH_RETENTION_CONFIRM:-YES}"
/opt/flowmesh/scripts/validate-retention-role.sh
exec /opt/flowmesh/scripts/cleanup-flowmesh-retention.sh

#!/usr/bin/env bash
# 作用：为维护容器设置 PostgreSQL 客户端连接参数，并执行保留策略脚本。
set -euo pipefail

export FLOWMESH_RETENTION_CONFIRM="${FLOWMESH_RETENTION_CONFIRM:-YES}"
exec /opt/flowmesh/scripts/cleanup-flowmesh-retention.sh

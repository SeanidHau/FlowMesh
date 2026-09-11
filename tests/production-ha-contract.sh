#!/usr/bin/env bash
# 作用：离线验证生产 HA 拓扑预检的必填参数、防覆盖和只读边界。

set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
script="${repo_root}/scripts/validate-production-ha.sh"

bash -n "${script}"
if FLOWMESH_HA_REPORT=/tmp/flowmesh-ha-contract.md "${script}" >/dev/null 2>&1; then
  echo '生产 HA 预检缺少依赖参数时应失败。' >&2
  exit 1
fi
grep -F -- 'FLOWMESH_HA_EXPECTED_PG_REPLICAS' "${script}" >/dev/null
grep -F -- 'FLOWMESH_HA_EXPECTED_REDIS_REPLICAS' "${script}" >/dev/null
grep -F -- 'pg_stat_replication' "${script}" >/dev/null
grep -F -- 'INFO replication' "${script}" >/dev/null
grep -F -- '拒绝覆盖已有生产 HA 报告' "${script}" >/dev/null
if grep -vE '^[[:space:]]*#' "${script}" | grep -E 'kubectl[[:space:]]+(apply|delete|patch|rollout|scale)|docker compose[[:space:]]+(up|down|start|stop)|redis-cli[[:space:]].*(SET|DEL|FLUSH)|psql[[:space:]].*(-f|--file)' >/dev/null; then
  echo '生产 HA 预检不得执行写操作。' >&2
  exit 1
fi

echo 'Production HA contract passed.'

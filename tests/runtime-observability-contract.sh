#!/usr/bin/env bash
# 作用：离线验证运行时观测预检的必填参数、只读边界和关键查询。

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
script="${repo_root}/scripts/validate-runtime-observability.sh"

bash -n "${script}"
if FLOWMESH_ALERTMANAGER_URL=http://alertmanager.local "${script}" >/dev/null 2>&1; then
  echo '运行时观测预检缺少 Prometheus 地址时应失败。' >&2
  exit 1
fi
if FLOWMESH_PROMETHEUS_URL=http://prometheus.local "${script}" >/dev/null 2>&1; then
  echo '运行时观测预检缺少 Alertmanager 地址时应失败。' >&2
  exit 1
fi
grep -F -- 'api/v1/query' "${script}" >/dev/null
grep -F -- 'api/v1/rules?type=alert' "${script}" >/dev/null
if grep -vE '^[[:space:]]*#' "${script}" | grep -E 'kubectl[[:space:]]+(apply|delete|patch|rollout|scale)|docker compose[[:space:]]+(up|down|start|stop)' >/dev/null; then
  echo '运行时观测预检不得执行集群或容器写操作。' >&2
  exit 1
fi

echo 'Runtime observability contract passed.'

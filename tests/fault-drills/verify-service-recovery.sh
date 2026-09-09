#!/usr/bin/env bash
# 作用：在已启动的 Compose 环境中验证单个应用服务停止后可恢复并重新通过健康检查。
# 脚本只操作显式传入的服务，不负责启动 Docker；执行前需确认当前环境允许短暂中断该服务。

set -euo pipefail

service="${1:-}"
health_url="${2:-}"
if [[ -z "${service}" || -z "${health_url}" ]]; then
  echo "用法：$0 <compose-service> <health-url>" >&2
  exit 64
fi
: "${FLOWMESH_CHAOS_CONFIRM:?请设置 FLOWMESH_CHAOS_CONFIRM=YES 才能执行故障演练}"
if [[ "${FLOWMESH_CHAOS_CONFIRM}" != "YES" ]]; then
  echo '拒绝执行：FLOWMESH_CHAOS_CONFIRM 必须精确为 YES。' >&2
  exit 64
fi

compose_file="${FLOWMESH_COMPOSE_FILE:-infra/compose/docker-compose.yml}"
env_file="${FLOWMESH_ENV_FILE:-.env}"
docker compose --env-file "${env_file}" -f "${compose_file}" stop "${service}"
docker compose --env-file "${env_file}" -f "${compose_file}" start "${service}"

for attempt in $(seq 1 60); do
  if curl --fail --silent "${health_url}" >/dev/null; then
    echo "故障恢复检查通过：${service}"
    exit 0
  fi
  sleep 2
done

echo "故障恢复检查失败：${service} 未在预期时间内恢复。" >&2
exit 1

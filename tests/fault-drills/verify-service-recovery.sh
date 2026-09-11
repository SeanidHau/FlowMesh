#!/usr/bin/env bash
# 作用：在已启动的 Compose 环境中验证单个应用服务停止后可恢复，并记录恢复耗时。
# 脚本只操作显式传入的服务，不负责启动 Docker；执行前需确认当前环境允许短暂中断该服务。

set -euo pipefail

service="${1:-}"
health_url="${2:-}"
if [[ -z "${service}" || -z "${health_url}" ]]; then
  echo "用法：$0 <compose-service> <health-url>" >&2
  exit 64
fi
if [[ -z "${FLOWMESH_CHAOS_CONFIRM:-}" ]]; then
  echo '请设置 FLOWMESH_CHAOS_CONFIRM=YES 才能执行故障演练。' >&2
  exit 64
fi
if [[ "${FLOWMESH_CHAOS_CONFIRM}" != "YES" ]]; then
  echo '拒绝执行：FLOWMESH_CHAOS_CONFIRM 必须精确为 YES。' >&2
  exit 64
fi

expected_rto_seconds="${FLOWMESH_DRILL_EXPECTED_RTO_SECONDS:-}"
if [[ -n "${expected_rto_seconds}" && ! "${expected_rto_seconds}" =~ ^[0-9]+$ ]]; then
  echo 'FLOWMESH_DRILL_EXPECTED_RTO_SECONDS 必须是非负整数。' >&2
  exit 64
fi

report_path="${FLOWMESH_DRILL_REPORT:-}"
if [[ -n "${report_path}" && -e "${report_path}" ]]; then
  echo "拒绝覆盖已有演练报告：${report_path}" >&2
  exit 64
fi

compose_file="${FLOWMESH_COMPOSE_FILE:-infra/compose/docker-compose.yml}"
env_file="${FLOWMESH_ENV_FILE:-.env}"
drill_started_at="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
recovery_started_at=''
recovery_finished_at=''
recovery_started_ns=''
recovery_finished_ns=''
recovery_seconds=''
service_needs_restore=false
restore_service() {
  local exit_code="$?"
  if [[ "${service_needs_restore}" == true ]]; then
    docker compose --env-file "${env_file}" -f "${compose_file}" start "${service}" >/dev/null 2>&1 || true
  fi
  exit "${exit_code}"
}
trap restore_service EXIT
docker compose --env-file "${env_file}" -f "${compose_file}" stop "${service}"
service_needs_restore=true

# 先确认停止动作确实造成健康检查失败，避免把未生效的故障注入误判为恢复成功。
service_unavailable=false
for attempt in $(seq 1 10); do
  if ! curl --fail --silent --show-error --max-time 2 "${health_url}" >/dev/null 2>&1; then
    service_unavailable=true
    break
  fi
  sleep 1
done
if [[ "${service_unavailable}" != true ]]; then
  echo "故障注入未生效：${service} 停止后健康检查仍然成功。" >&2
  exit 1
fi

recovery_started_at="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
if [[ "$(uname -s)" == Darwin* ]]; then
  recovery_started_ns="$(python3 -c 'import time; print(time.monotonic_ns())')"
else
  recovery_started_ns="$(date +%s%N)"
fi
docker compose --env-file "${env_file}" -f "${compose_file}" start "${service}"

for attempt in $(seq 1 60); do
  if curl --fail --silent "${health_url}" >/dev/null; then
    service_needs_restore=false
    recovery_finished_at="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
    if [[ "$(uname -s)" == Darwin* ]]; then
      recovery_finished_ns="$(python3 -c 'import time; print(time.monotonic_ns())')"
    else
      recovery_finished_ns="$(date +%s%N)"
    fi
    recovery_seconds="$(( (recovery_finished_ns - recovery_started_ns + 999999999) / 1000000000 ))"
    if [[ -n "${expected_rto_seconds}" && "${recovery_seconds}" -gt "${expected_rto_seconds}" ]]; then
      echo "故障恢复超出目标 RTO：${recovery_seconds}s > ${expected_rto_seconds}s。" >&2
      exit 1
    fi

    if [[ -n "${report_path}" ]]; then
      report_directory="$(dirname "${report_path}")"
      mkdir -p "${report_directory}"
      if ! (
        set -o noclobber
        {
          echo '# 故障恢复演练报告'
          echo
          echo "- 服务：\`${service}\`"
          echo "- 健康检查：\`${health_url}\`"
          echo "- 开始时间（UTC）：\`${drill_started_at}\`"
          echo "- 故障恢复计时开始（UTC）：\`${recovery_started_at}\`"
          echo "- 故障恢复完成（UTC）：\`${recovery_finished_at}\`"
          echo "- 停止后健康检查：\`FAIL（符合预期）\`"
          echo "- 恢复耗时：\`${recovery_seconds}s\`"
          if [[ -n "${expected_rto_seconds}" ]]; then
            echo "- 目标 RTO：\`${expected_rto_seconds}s\`"
            echo '- 结果：`PASS`'
          else
            echo '- 结果：`RECOVERED`（未设置目标 RTO）'
          fi
          echo
          echo '> 该报告只证明 Compose 服务停止后应用进程的恢复耗时，不证明数据库、Redis、RocketMQ 或对象存储的故障切换能力。'
        } > "${report_path}"
      ); then
        echo "无法创建演练报告，拒绝覆盖已有文件：${report_path}" >&2
        exit 64
      fi
    fi
    echo "故障恢复检查通过：${service}（${recovery_seconds}s）"
    exit 0
  fi
  sleep 2
done

echo "故障恢复检查失败：${service} 未在预期时间内恢复。" >&2
exit 1

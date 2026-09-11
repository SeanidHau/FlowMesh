#!/usr/bin/env bash

# 作用：离线验证故障恢复演练脚本的确认、RTO 和报告防覆盖门禁，不启动 Docker。

set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCRIPT="${ROOT_DIR}/tests/fault-drills/verify-service-recovery.sh"
TEMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/flowmesh-recovery-contract.XXXXXX")"
trap 'rm -rf -- "${TEMP_DIR}"' EXIT

assert_rejected() {
  local expected_message="$1"
  shift
  set +e
  output="$(${SCRIPT} "$@" 2>&1)"
  status=$?
  set -e
  [[ "${status}" -eq 64 ]] || {
    echo "预期参数校验失败，实际退出码为 ${status}。" >&2
    echo "${output}" >&2
    exit 1
  }
  [[ "${output}" == *"${expected_message}"* ]] || {
    echo "未找到预期错误：${expected_message}" >&2
    echo "${output}" >&2
    exit 1
  }
}

assert_rejected 'FLOWMESH_CHAOS_CONFIRM' risk-service http://localhost:8084/actuator/health
FLOWMESH_CHAOS_CONFIRM=NO assert_rejected '必须精确为 YES' risk-service http://localhost:8084/actuator/health
FLOWMESH_CHAOS_CONFIRM=YES FLOWMESH_DRILL_EXPECTED_RTO_SECONDS=30s \
  assert_rejected '必须是非负整数' risk-service http://localhost:8084/actuator/health

existing_report="${TEMP_DIR}/existing.md"
touch "${existing_report}"
set +e
output="$(FLOWMESH_CHAOS_CONFIRM=YES FLOWMESH_DRILL_REPORT="${existing_report}" \
  "${SCRIPT}" risk-service http://localhost:8084/actuator/health 2>&1)"
status=$?
set -e
[[ "${status}" -eq 64 ]] || {
  echo "已有报告时应拒绝覆盖，实际退出码为 ${status}。" >&2
  echo "${output}" >&2
  exit 1
}
[[ "${output}" == *'拒绝覆盖已有演练报告'* ]] || {
  echo "已有报告时未输出防覆盖提示。" >&2
  echo "${output}" >&2
  exit 1
}

echo '故障恢复演练脚本契约检查通过。'

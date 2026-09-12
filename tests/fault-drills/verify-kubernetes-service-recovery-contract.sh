#!/usr/bin/env bash
# 作用：离线验证 Kubernetes 故障演练脚本的确认、组件白名单、RTO 和报告防覆盖门禁。

set -Eeuo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
script="${root_dir}/tests/fault-drills/verify-kubernetes-service-recovery.sh"
temporary_directory="$(mktemp -d "${TMPDIR:-/tmp}/flowmesh-k8s-recovery-contract.XXXXXX")"
trap 'rm -rf -- "${temporary_directory}"' EXIT

bash -n "${script}"

assert_rejected() {
  local expected_message="$1"
  shift
  set +e
  output="$(${script} "$@" 2>&1)"
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

assert_rejected 'FLOWMESH_K8S_CHAOS_CONFIRM' gateway flowmesh http://localhost:8080/actuator/health
FLOWMESH_K8S_CHAOS_CONFIRM=NO assert_rejected '必须精确为 YES' gateway flowmesh http://localhost:8080/actuator/health
FLOWMESH_K8S_CHAOS_CONFIRM=YES assert_rejected '不允许对该 FlowMesh 组件' unknown flowmesh http://localhost:8080/actuator/health
FLOWMESH_K8S_CHAOS_CONFIRM=YES FLOWMESH_HELM_RELEASE='bad/release' \
  assert_rejected 'Helm Release 不是合法名称' gateway flowmesh http://localhost:8080/actuator/health
FLOWMESH_K8S_CHAOS_CONFIRM=YES FLOWMESH_DRILL_EXPECTED_RTO_SECONDS=30s \
  assert_rejected '必须是非负整数' gateway flowmesh http://localhost:8080/actuator/health
FLOWMESH_K8S_CHAOS_CONFIRM=YES FLOWMESH_K8S_DRILL_TIMEOUT_SECONDS=0 \
  assert_rejected '必须是正整数' gateway flowmesh http://localhost:8080/actuator/health
FLOWMESH_K8S_CHAOS_CONFIRM=YES FLOWMESH_K8S_DRILL_REPORT="${temporary_directory}/new-report.md" \
  assert_rejected '必须提供 40 位小写 FLOWMESH_IMAGE_TAG' gateway flowmesh http://localhost:8080/actuator/health
FLOWMESH_K8S_CHAOS_CONFIRM=YES FLOWMESH_IMAGE_TAG=0123456789012345678901234567890123456789 \
  FLOWMESH_EVIDENCE_ENVIRONMENT=production-cluster-a \
  assert_rejected '健康检查 URL 必须是' gateway flowmesh file:///etc/passwd

existing_report="${temporary_directory}/existing.md"
touch "${existing_report}"
set +e
output="$(FLOWMESH_K8S_CHAOS_CONFIRM=YES FLOWMESH_K8S_DRILL_REPORT="${existing_report}" \
  "${script}" gateway flowmesh http://localhost:8080/actuator/health 2>&1)"
status=$?
set -e
[[ "${status}" -eq 64 ]] || {
  echo "已有报告时应拒绝覆盖，实际退出码为 ${status}。" >&2
  echo "${output}" >&2
  exit 1
}
[[ "${output}" == *'拒绝覆盖已有演练报告'* ]] || {
  echo '已有报告时未输出防覆盖提示。' >&2
  echo "${output}" >&2
  exit 1
}

grep -F -- 'kubectl delete pod' "${script}" >/dev/null
grep -F -- 'readyReplicas' "${script}" >/dev/null
grep -F -- 'app.kubernetes.io/component' "${script}" >/dev/null
grep -F -- 'FLOWMESH_EVIDENCE_ENVIRONMENT' "${script}" >/dev/null
grep -F -- '证据摘要' "${script}" >/dev/null

echo 'Kubernetes service recovery contract passed.'

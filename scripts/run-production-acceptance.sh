#!/usr/bin/env bash
# 作用：在目标生产运维环境中串联只读发布验收，并生成不可覆盖的证据报告。
# 脚本不会执行 Kubernetes 写操作、数据库写操作或故障切换；HA、恢复和 RTO/RPO 仍需单独演练。

set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
report_path="${FLOWMESH_ACCEPTANCE_REPORT:-}"
if [[ -z "${report_path}" ]]; then
  echo '请设置 FLOWMESH_ACCEPTANCE_REPORT 指定新的验收报告路径。' >&2
  exit 64
fi
if [[ -e "${report_path}" ]]; then
  echo "拒绝覆盖已有验收报告：${report_path}" >&2
  exit 64
fi

expect_prometheus_rule="${FLOWMESH_EXPECT_PROMETHEUS_RULE:-true}"
case "${expect_prometheus_rule}" in
  true|false)
    ;;
  *)
    echo 'FLOWMESH_EXPECT_PROMETHEUS_RULE 必须是 true 或 false。' >&2
    exit 64
    ;;
esac
export FLOWMESH_EXPECT_PROMETHEUS_RULE="${expect_prometheus_rule}"

report_directory="$(dirname "${report_path}")"
mkdir -p "${report_directory}"
temporary_directory="$(mktemp -d "${TMPDIR:-/tmp}/flowmesh-production-acceptance.XXXXXX")"
report_body="${temporary_directory}/report.md"
overall_status=0
started_at="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"

cleanup() {
  local exit_code=$?
  rm -rf -- "${temporary_directory}"
  exit "${exit_code}"
}
trap cleanup EXIT

{
  echo '# FlowMesh 生产验收报告'
  echo
  echo "- 开始时间（UTC）：\`${started_at}\`"
  echo "- Kubernetes 命名空间：\`${FLOWMESH_K8S_NAMESPACE:-flowmesh}\`"
  echo "- Helm Release：\`${FLOWMESH_HELM_RELEASE:-flowmesh}\`"
  echo "- 镜像提交：\`${FLOWMESH_IMAGE_TAG:-未设置}\`"
  echo "- 期望 Prometheus Operator 资源：\`${expect_prometheus_rule}\`"
  echo
  echo '> 本报告只记录仓库提供的只读预检结果，不证明外部依赖 HA、故障切换、备份恢复或 RTO/RPO 已完成。'
  echo
} > "${report_body}"

run_check() {
  local name="$1"
  shift
  local output_file="${temporary_directory}/$(printf '%s' "${name}" | tr -c '[:alnum:]' '_').log"
  local check_status='PASS'
  local exit_code=0

  if "$@" >"${output_file}" 2>&1; then
    :
  else
    exit_code=$?
    check_status='FAIL'
    overall_status=1
  fi

  {
    echo "## ${name}"
    echo
    echo "- 结果：\`${check_status}\`"
    echo "- 退出码：\`${exit_code}\`"
    echo
    echo '```text'
    sed -n '1,240p' "${output_file}"
    echo '```'
    echo
  } >> "${report_body}"
}

run_check '镜像签名与不可变标签' "${ROOT_DIR}/scripts/verify-flowmesh-images.sh"
run_check 'Kubernetes 生产 smoke test' "${ROOT_DIR}/tests/kubernetes-production-smoke.sh"
run_check '外部依赖 TLS 与连通性预检' "${ROOT_DIR}/scripts/validate-production-dependencies.sh"
run_check '生命周期维护角色权限预检' "${ROOT_DIR}/scripts/validate-retention-role.sh"

{
  echo "- 完成时间（UTC）：\`$(date -u '+%Y-%m-%dT%H:%M:%SZ')\`"
  if [[ "${overall_status}" -eq 0 ]]; then
    echo '- 总结果：`PASS`'
  else
    echo '- 总结果：`FAIL`'
  fi
} >> "${report_body}"

if ! (
  set -o noclobber
  cat "${report_body}" > "${report_path}"
); then
  echo "无法创建验收报告，拒绝覆盖已有文件：${report_path}" >&2
  exit 64
fi

if [[ "${overall_status}" -ne 0 ]]; then
  echo "生产验收失败，报告已保存：${report_path}" >&2
  exit 1
fi
echo "生产验收通过，报告已保存：${report_path}"

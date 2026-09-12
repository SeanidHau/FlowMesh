#!/usr/bin/env bash
# 作用：在目标 Kubernetes 集群中删除一个应用 Pod，验证 Deployment 自愈、ReadyReplicas 和健康检查恢复耗时。
# 脚本只操作显式白名单内的 FlowMesh 应用组件；执行前必须由值班人员显式确认，且不会启动本地 Docker。

set -Eeuo pipefail

component="${1:-}"
namespace="${2:-}"
health_url="${3:-}"
release="${FLOWMESH_HELM_RELEASE:-flowmesh}"
report_path="${FLOWMESH_K8S_DRILL_REPORT:-}"
expected_rto_seconds="${FLOWMESH_DRILL_EXPECTED_RTO_SECONDS:-}"
timeout_seconds="${FLOWMESH_K8S_DRILL_TIMEOUT_SECONDS:-180}"

if [[ -z "${component}" || -z "${namespace}" || -z "${health_url}" ]]; then
  echo "用法：FLOWMESH_K8S_CHAOS_CONFIRM=YES $0 <component> <namespace> <health-url>" >&2
  exit 64
fi
if [[ "${FLOWMESH_K8S_CHAOS_CONFIRM:-}" != "YES" ]]; then
  echo '拒绝执行：FLOWMESH_K8S_CHAOS_CONFIRM 必须精确为 YES。' >&2
  exit 64
fi
case "${component}" in
  gateway|iam|supplier|workflow|risk|notification-audit)
    ;;
  *)
    echo "拒绝执行：不允许对该 FlowMesh 组件执行 Pod 故障演练：${component}" >&2
    exit 64
    ;;
esac
if [[ ! "${namespace}" =~ ^[a-z0-9]([-a-z0-9]*[a-z0-9])?$ ]]; then
  echo "Kubernetes namespace 不是合法名称：${namespace}" >&2
  exit 64
fi
if [[ ! "${release}" =~ ^[a-z0-9]([-a-z0-9]*[a-z0-9])?$ ]]; then
  echo "Helm Release 不是合法名称：${release}" >&2
  exit 64
fi
if [[ -n "${expected_rto_seconds}" && ! "${expected_rto_seconds}" =~ ^[0-9]+$ ]]; then
  echo 'FLOWMESH_DRILL_EXPECTED_RTO_SECONDS 必须是非负整数。' >&2
  exit 64
fi
if [[ ! "${timeout_seconds}" =~ ^[1-9][0-9]*$ ]]; then
  echo 'FLOWMESH_K8S_DRILL_TIMEOUT_SECONDS 必须是正整数。' >&2
  exit 64
fi
if [[ -n "${report_path}" && -e "${report_path}" ]]; then
  echo "拒绝覆盖已有演练报告：${report_path}" >&2
  exit 64
fi
if [[ -n "${report_path}" ]]; then
  image_tag="${FLOWMESH_IMAGE_TAG:-}"
  evidence_environment="${FLOWMESH_EVIDENCE_ENVIRONMENT:-}"
  if [[ ! "${image_tag}" =~ ^[0-9a-f]{40}$ ]]; then
    echo '生成生产恢复报告时必须提供 40 位小写 FLOWMESH_IMAGE_TAG。' >&2
    exit 64
  fi
  if [[ -z "${evidence_environment}" || "${evidence_environment}" == *$'\n'* \
    || "${evidence_environment}" == *$'\r'* || "${evidence_environment}" == *'`'* ]]; then
    echo '生成生产恢复报告时必须提供不含换行或 Markdown 控制字符的 FLOWMESH_EVIDENCE_ENVIRONMENT。' >&2
    exit 64
  fi
fi
if [[ "${health_url}" != http://* && "${health_url}" != https://* ]] \
  || [[ "${health_url}" == *$'\n'* || "${health_url}" == *$'\r'* || "${health_url}" == *'`'* \
    || "${health_url}" == *[[:space:]]* ]]; then
  echo '健康检查 URL 必须是无空白、无 Markdown 控制字符的 HTTP(S) URL。' >&2
  exit 64
fi

for command_name in kubectl curl date; do
  command -v "${command_name}" >/dev/null || {
    echo "Kubernetes 故障演练缺少命令：${command_name}" >&2
    exit 127
  }
done

monotonic_ns() {
  if [[ "$(uname -s)" == Darwin* ]]; then
    command -v python3 >/dev/null || {
      echo 'macOS 故障演练需要 python3 获取单调时钟。' >&2
      return 127
    }
    python3 -c 'import time; print(time.monotonic_ns())'
  else
    date +%s%N
  fi
}

deployment="${release}-${component}"
selector="app.kubernetes.io/instance=${release},app.kubernetes.io/component=${component}"
kubectl get deployment "${deployment}" --namespace "${namespace}" >/dev/null
desired_replicas="$(kubectl get deployment "${deployment}" --namespace "${namespace}" -o jsonpath='{.spec.replicas}')"
[[ "${desired_replicas}" =~ ^[1-9][0-9]*$ ]] || {
  echo "Deployment 副本数无效：${deployment}=${desired_replicas}" >&2
  exit 1
}

pod_names="$(kubectl get pods --namespace "${namespace}" --selector "${selector}" \
  --field-selector=status.phase=Running \
  -o jsonpath='{range .items[*]}{.metadata.name}{"\n"}{end}')"
old_pod="$(printf '%s\n' "${pod_names}" | sed '/^$/d' | head -n 1)"
[[ -n "${old_pod}" ]] || {
  echo "未找到可供故障演练的 Running Pod：${selector}" >&2
  exit 1
}

drill_started_at="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
recovery_started_at="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
recovery_started_ns="$(monotonic_ns)"
kubectl delete pod "${old_pod}" --namespace "${namespace}" --wait=false

deadline=$(( $(date +%s) + timeout_seconds ))
recovery_finished_at=''
recovery_finished_ns=''
recovery_seconds=''
ready_replicas='0'
while [[ "$(date +%s)" -lt "${deadline}" ]]; do
  current_pods=''
  if current_pods="$(kubectl get pods --namespace "${namespace}" --selector "${selector}" \
    -o jsonpath='{range .items[*]}{.metadata.name}{"\n"}{end}' 2>/dev/null)"; then
    old_pod_gone=true
    if printf '%s\n' "${current_pods}" | grep -Fxq -- "${old_pod}"; then
      old_pod_gone=false
    fi
  else
    old_pod_gone=false
  fi

  ready_replicas='0'
  ready_value="$(kubectl get deployment "${deployment}" --namespace "${namespace}" \
    -o jsonpath='{.status.readyReplicas}' 2>/dev/null || true)"
  if [[ "${ready_value}" =~ ^[0-9]+$ ]]; then
    ready_replicas="${ready_value}"
  fi

  if [[ "${old_pod_gone}" == true && "${ready_replicas}" -ge "${desired_replicas}" ]] \
    && curl --fail --silent --show-error --max-time 5 "${health_url}" >/dev/null 2>&1; then
    recovery_finished_at="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
    recovery_finished_ns="$(monotonic_ns)"
    recovery_seconds="$(( (recovery_finished_ns - recovery_started_ns + 999999999) / 1000000000 ))"
    break
  fi
  sleep 2
done

if [[ -z "${recovery_seconds}" ]]; then
  echo "Kubernetes 故障恢复超时：${deployment} 未在 ${timeout_seconds}s 内恢复。" >&2
  exit 1
fi
if [[ -n "${expected_rto_seconds}" && "${recovery_seconds}" -gt "${expected_rto_seconds}" ]]; then
  echo "Kubernetes 故障恢复超出目标 RTO：${recovery_seconds}s > ${expected_rto_seconds}s。" >&2
  exit 1
fi

if [[ -n "${report_path}" ]]; then
  report_directory="$(dirname "${report_path}")"
  mkdir -p "${report_directory}"
  if ! (
    set -o noclobber
    {
      echo '# Kubernetes 故障恢复演练报告'
      echo
      echo "- 证据摘要：应用 Pod 删除后 Deployment 自愈、ReadyReplicas 和健康检查恢复结果。"
      echo "- 环境标识：\`${evidence_environment}\`"
      echo "- 镜像提交：\`${image_tag}\`"
      echo "- 组件：\`${component}\`"
      echo "- Deployment：\`${deployment}\`"
      echo "- 被删除 Pod：\`${old_pod}\`"
      echo "- 健康检查：\`${health_url}\`"
      echo "- 开始时间（UTC）：\`${drill_started_at}\`"
      echo "- 故障恢复计时开始（UTC）：\`${recovery_started_at}\`"
      echo "- 故障恢复完成（UTC）：\`${recovery_finished_at}\`"
      echo "- 检查命令：\`kubectl delete pod ${old_pod} --namespace ${namespace} --wait=false\`"
      echo "- ReadyReplicas：\`${ready_replicas}/${desired_replicas}\`"
      echo "- 恢复耗时：\`${recovery_seconds}s\`"
      if [[ -n "${expected_rto_seconds}" ]]; then
        echo "- 目标 RTO：\`${expected_rto_seconds}s\`"
        echo '- 结果：`PASS`'
      else
        echo '- 结果：`RECOVERED`（未设置目标 RTO）'
      fi
      echo
      echo '> 该报告只证明应用 Pod 删除后 Deployment 自愈和健康检查恢复，不证明数据库、Redis、RocketMQ 或对象存储的故障切换能力。'
    } > "${report_path}"
  ); then
    echo "无法创建演练报告，拒绝覆盖已有文件：${report_path}" >&2
    exit 64
  fi
fi

echo "Kubernetes 故障恢复检查通过：${deployment}（${recovery_seconds}s）"

#!/usr/bin/env bash
# 作用：执行经过校验的 FlowMesh 生产 Helm 发布，并在发布完成后执行只读 Kubernetes smoke test。
# 该脚本不接收业务凭据参数；运行时 Secret 必须预先创建并通过 global.existingSecret 引用。

set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
chart_path="${FLOWMESH_HELM_CHART:-${ROOT_DIR}/infra/helm/flowmesh}"
values_path="${FLOWMESH_HELM_VALUES:-${chart_path}/values-production.yaml}"
release="${FLOWMESH_HELM_RELEASE:-flowmesh}"
namespace="${FLOWMESH_K8S_NAMESPACE:-flowmesh}"
helm_timeout="${FLOWMESH_HELM_TIMEOUT:-10m}"
image_tag="${FLOWMESH_IMAGE_TAG:-}"
runtime_secret="${FLOWMESH_RUNTIME_SECRET_NAME:-flowmesh-runtime-secrets}"
ingress_namespace="${FLOWMESH_INGRESS_NAMESPACE:-ingress-nginx}"
monitoring_namespace="${FLOWMESH_MONITORING_NAMESPACE:-monitoring}"
prometheus_release="${FLOWMESH_PROMETHEUS_RELEASE:-kube-prometheus-stack}"
expect_prometheus_rule="${FLOWMESH_EXPECT_PROMETHEUS_RULE:-true}"

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    printf '生产发布缺少命令：%s\n' "$1" >&2
    exit 127
  }
}

require_value() {
  local name="$1"
  local value="$2"
  [[ -n "${value}" ]] || {
    printf '生产发布缺少环境变量：%s\n' "${name}" >&2
    exit 64
  }
}

require_safe_name() {
  local name="$1"
  local value="$2"
  [[ "${value}" =~ ^[a-z0-9]([-a-z0-9]*[a-z0-9])?$ ]] || {
    printf '%s 必须是合法的 Kubernetes 名称：%s\n' "${name}" "${value}" >&2
    exit 64
  }
}

reject_placeholder() {
  local name="$1"
  local value="$2"
  case "${value}" in
    localhost|127.*|0.0.0.0|*.example.com|*.internal.example.com|*://localhost*|*://127.*)
      printf '%s 仍是示例或本机地址，禁止用于生产发布：%s\n' "${name}" "${value}" >&2
      exit 64
      ;;
  esac
}

require_command helm
require_command kubectl
require_command cosign

require_value FLOWMESH_IMAGE_TAG "${image_tag}"
[[ "${image_tag}" =~ ^[0-9a-f]{40}$ ]] || {
  echo 'FLOWMESH_IMAGE_TAG 必须是 40 位小写 Git 提交 SHA。' >&2
  exit 64
}
require_safe_name FLOWMESH_HELM_RELEASE "${release}"
require_safe_name FLOWMESH_K8S_NAMESPACE "${namespace}"
require_safe_name FLOWMESH_RUNTIME_SECRET_NAME "${runtime_secret}"
require_safe_name FLOWMESH_INGRESS_NAMESPACE "${ingress_namespace}"
require_safe_name FLOWMESH_MONITORING_NAMESPACE "${monitoring_namespace}"
require_safe_name FLOWMESH_PROMETHEUS_RELEASE "${prometheus_release}"

require_value FLOWMESH_INGRESS_HOST "${FLOWMESH_INGRESS_HOST:-}"
require_value FLOWMESH_INGRESS_TLS_SECRET_NAME "${FLOWMESH_INGRESS_TLS_SECRET_NAME:-}"
require_value FLOWMESH_POSTGRES_HOST "${FLOWMESH_POSTGRES_HOST:-}"
require_value FLOWMESH_REDIS_HOST "${FLOWMESH_REDIS_HOST:-}"
require_value FLOWMESH_ROCKETMQ_NAMESRV_ADDR "${FLOWMESH_ROCKETMQ_NAMESRV_ADDR:-}"
require_value FLOWMESH_OBJECT_STORAGE_ENDPOINT "${FLOWMESH_OBJECT_STORAGE_ENDPOINT:-}"
require_value FLOWMESH_CLAMAV_HOST "${FLOWMESH_CLAMAV_HOST:-}"
require_value FLOWMESH_BACKUP_POSTGRES_HOST "${FLOWMESH_BACKUP_POSTGRES_HOST:-}"
require_value FLOWMESH_BACKUP_POSTGRES_USER "${FLOWMESH_BACKUP_POSTGRES_USER:-}"
require_value FLOWMESH_BACKUP_S3_URI "${FLOWMESH_BACKUP_S3_URI:-}"
require_value FLOWMESH_BACKUP_SECRET_NAME "${FLOWMESH_BACKUP_SECRET_NAME:-}"
require_value FLOWMESH_RETENTION_POSTGRES_HOST "${FLOWMESH_RETENTION_POSTGRES_HOST:-}"
require_value FLOWMESH_RETENTION_SECRET_NAME "${FLOWMESH_RETENTION_SECRET_NAME:-}"
require_value FLOWMESH_NOTIFICATION_WEBHOOK_URL "${FLOWMESH_NOTIFICATION_WEBHOOK_URL:-}"
require_value FLOWMESH_NETWORK_POLICY_EXTERNAL_CIDRS "${FLOWMESH_NETWORK_POLICY_EXTERNAL_CIDRS:-}"
require_safe_name FLOWMESH_INGRESS_TLS_SECRET_NAME "${FLOWMESH_INGRESS_TLS_SECRET_NAME:-}"
require_safe_name FLOWMESH_BACKUP_SECRET_NAME "${FLOWMESH_BACKUP_SECRET_NAME:-}"
require_safe_name FLOWMESH_BACKUP_POSTGRES_USER "${FLOWMESH_BACKUP_POSTGRES_USER:-}"
require_safe_name FLOWMESH_RETENTION_SECRET_NAME "${FLOWMESH_RETENTION_SECRET_NAME:-}"

reject_placeholder FLOWMESH_INGRESS_HOST "${FLOWMESH_INGRESS_HOST}"
reject_placeholder FLOWMESH_POSTGRES_HOST "${FLOWMESH_POSTGRES_HOST}"
reject_placeholder FLOWMESH_REDIS_HOST "${FLOWMESH_REDIS_HOST}"
reject_placeholder FLOWMESH_OBJECT_STORAGE_ENDPOINT "${FLOWMESH_OBJECT_STORAGE_ENDPOINT}"
reject_placeholder FLOWMESH_NOTIFICATION_WEBHOOK_URL "${FLOWMESH_NOTIFICATION_WEBHOOK_URL}"

[[ "${FLOWMESH_OBJECT_STORAGE_ENDPOINT}" == https://* ]] || {
  echo 'FLOWMESH_OBJECT_STORAGE_ENDPOINT 必须使用 HTTPS。' >&2
  exit 64
}
[[ "${FLOWMESH_NOTIFICATION_WEBHOOK_URL}" == https://* ]] || {
  echo 'FLOWMESH_NOTIFICATION_WEBHOOK_URL 必须使用 HTTPS。' >&2
  exit 64
}
[[ "${expect_prometheus_rule}" == true || "${expect_prometheus_rule}" == false ]] || {
  echo 'FLOWMESH_EXPECT_PROMETHEUS_RULE 必须是 true 或 false。' >&2
  exit 64
}
IFS=',' read -r -a nameserver_endpoints <<< "${FLOWMESH_ROCKETMQ_NAMESRV_ADDR}"
if [[ "${#nameserver_endpoints[@]}" -lt 2 ]]; then
  echo 'FLOWMESH_ROCKETMQ_NAMESRV_ADDR 至少需要两个逗号分隔的 NameServer 地址。' >&2
  exit 64
fi
[[ -d "${chart_path}" && -f "${chart_path}/Chart.yaml" ]] || {
  echo "Helm Chart 不存在：${chart_path}" >&2
  exit 2
}
[[ -f "${values_path}" ]] || {
  echo "生产 values 文件不存在：${values_path}" >&2
  exit 2
}

external_cidrs=()
IFS=',' read -r -a raw_cidrs <<< "${FLOWMESH_NETWORK_POLICY_EXTERNAL_CIDRS}"
for cidr in "${raw_cidrs[@]}"; do
  cidr="$(printf '%s' "${cidr}" | tr -d '[:space:]')"
  [[ -n "${cidr}" ]] || continue
  [[ "${cidr}" =~ ^[0-9A-Fa-f:.]+/[0-9]+$ ]] || {
    printf 'FLOWMESH_NETWORK_POLICY_EXTERNAL_CIDRS 包含无效 CIDR：%s\n' "${cidr}" >&2
    exit 64
  }
  external_cidrs+=("${cidr}")
done
[[ "${#external_cidrs[@]}" -gt 0 ]] || {
  echo 'FLOWMESH_NETWORK_POLICY_EXTERNAL_CIDRS 至少需要一个 CIDR。' >&2
  exit 64
}
rocketmq_namesrv_helm_value="${FLOWMESH_ROCKETMQ_NAMESRV_ADDR//,/\\,}"

# 记录升级前的稳定 Helm revision，发布后的 smoke 失败时回滚到该版本。
previous_revision=''
if helm status "${release}" --namespace "${namespace}" >/dev/null 2>&1; then
  previous_revision="$(helm history "${release}" --namespace "${namespace}" --max 1 | awk 'NR == 3 {print $1}')"
  [[ "${previous_revision}" =~ ^[0-9]+$ ]] || {
    echo "无法解析 Helm Release 的当前 revision：${release}" >&2
    exit 2
  }
fi

FLOWMESH_IMAGE_TAG="${image_tag}" "${ROOT_DIR}/scripts/verify-flowmesh-images.sh"
bash "${ROOT_DIR}/scripts/validate-production-config.sh" "${values_path}"

helm_overrides=(
  --set-string "global.imageTag=${image_tag}"
  --set-string "global.existingSecret=${runtime_secret}"
  --set-string "postgresql.host=${FLOWMESH_POSTGRES_HOST}"
  --set-string "redis.host=${FLOWMESH_REDIS_HOST}"
  --set-string "rocketmq.namesrvAddr=${rocketmq_namesrv_helm_value}"
  --set-string "objectStorage.endpoint=${FLOWMESH_OBJECT_STORAGE_ENDPOINT}"
  --set-string "fileScan.host=${FLOWMESH_CLAMAV_HOST}"
  --set-string "backup.postgres.host=${FLOWMESH_BACKUP_POSTGRES_HOST}"
  --set-string "backup.postgres.user=${FLOWMESH_BACKUP_POSTGRES_USER}"
  --set-string "backup.s3.uri=${FLOWMESH_BACKUP_S3_URI}"
  --set-string "backup.credentialsSecret=${FLOWMESH_BACKUP_SECRET_NAME}"
  --set-string "retention.postgres.host=${FLOWMESH_RETENTION_POSTGRES_HOST}"
  --set-string "retention.credentialsSecret=${FLOWMESH_RETENTION_SECRET_NAME}"
  --set-string "services.notificationAudit.notificationDelivery.webhookUrl=${FLOWMESH_NOTIFICATION_WEBHOOK_URL}"
  --set-string "networkPolicy.ingressNamespace=${ingress_namespace}"
  --set-string "networkPolicy.monitoringNamespace=${monitoring_namespace}"
  --set-string "ingress.host=${FLOWMESH_INGRESS_HOST}"
  --set-string "ingress.tls[0].secretName=${FLOWMESH_INGRESS_TLS_SECRET_NAME}"
  --set-string "ingress.tls[0].hosts[0]=${FLOWMESH_INGRESS_HOST}"
  --set "observability.serviceMonitor.enabled=${expect_prometheus_rule}"
  --set "observability.prometheusRule.enabled=${expect_prometheus_rule}"
  --set-string "observability.serviceMonitor.labels.release=${prometheus_release}"
  --set-string "observability.prometheusRule.labels.release=${prometheus_release}"
)

for index in "${!external_cidrs[@]}"; do
  helm_overrides+=("--set-string" "networkPolicy.egress.externalCidrs[${index}]=${external_cidrs[${index}]}")
done

helm lint "${chart_path}" -f "${values_path}" "${helm_overrides[@]}"
helm upgrade --install "${release}" "${chart_path}" \
  --namespace "${namespace}" \
  --create-namespace \
  --atomic \
  --wait \
  --timeout "${helm_timeout}" \
  -f "${values_path}" \
  "${helm_overrides[@]}"

if FLOWMESH_IMAGE_TAG="${image_tag}" \
FLOWMESH_K8S_NAMESPACE="${namespace}" \
FLOWMESH_HELM_RELEASE="${release}" \
FLOWMESH_EXPECT_PROMETHEUS_RULE="${expect_prometheus_rule}" \
FLOWMESH_INGRESS_NAMESPACE="${ingress_namespace}" \
FLOWMESH_BACKUP_POSTGRES_USER="${FLOWMESH_BACKUP_POSTGRES_USER}" \
  "${ROOT_DIR}/tests/kubernetes-production-smoke.sh"; then
  exit 0
else
  smoke_status=$?
fi

rollback_status=0
if [[ -n "${previous_revision}" ]]; then
  echo "发布后 smoke 失败，回滚 Helm Release ${release} 到 revision ${previous_revision}。" >&2
  if helm rollback "${release}" "${previous_revision}" \
    --namespace "${namespace}" \
    --wait \
    --timeout "${helm_timeout}"; then
    :
  else
    rollback_status=$?
  fi
else
  echo '首次发布后的 smoke 失败，卸载本次应用资源并保留 Helm 历史。' >&2
  if helm uninstall "${release}" \
    --namespace "${namespace}" \
    --keep-history \
    --wait \
    --timeout "${helm_timeout}"; then
    :
  else
    rollback_status=$?
  fi
fi

if [[ "${rollback_status}" -ne 0 ]]; then
  echo "发布后 smoke 失败，且自动回滚也失败；请立即人工处置 Release：${release}" >&2
fi
exit "${smoke_status}"

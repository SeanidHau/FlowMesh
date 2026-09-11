#!/usr/bin/env bash
# 作用：只读检查目标 Kubernetes 集群中的 FlowMesh 生产资源是否满足发布后验收条件。
# 脚本不执行 kubectl apply、rollout restart 或其他写操作。
set -euo pipefail

command -v kubectl >/dev/null 2>&1 || {
  echo '未找到 kubectl，请在目标集群运维环境执行。' >&2
  exit 127
}

namespace="${FLOWMESH_K8S_NAMESPACE:-flowmesh}"
release="${FLOWMESH_HELM_RELEASE:-flowmesh}"
expected_image_tag="${FLOWMESH_IMAGE_TAG:-}"
expect_prometheus_rule="${FLOWMESH_EXPECT_PROMETHEUS_RULE:-false}"
deployment_selector="app.kubernetes.io/instance=${release}"
backup_cronjob="${release}-flowmesh-postgres-backup"

if [[ -z "${expected_image_tag}" || ! "${expected_image_tag}" =~ ^[0-9a-f]{40}$ ]]; then
  echo 'FLOWMESH_IMAGE_TAG 必须是 40 位小写 Git 提交 SHA。' >&2
  exit 2
fi

kubectl get namespace "${namespace}" >/dev/null

mapfile -t deployments < <(
  kubectl -n "${namespace}" get deployment \
    -l "${deployment_selector}" \
    -o jsonpath='{range .items[*]}{.metadata.name}{"\n"}{end}' | sort
)
expected_deployments=(
  "${release}-flowmesh-gateway"
  "${release}-flowmesh-iam"
  "${release}-flowmesh-supplier"
  "${release}-flowmesh-workflow"
  "${release}-flowmesh-risk"
  "${release}-flowmesh-notification-audit"
)
mapfile -t expected_deployments < <(printf '%s\n' "${expected_deployments[@]}" | sort)
if [[ "${deployments[*]}" != "${expected_deployments[*]}" ]]; then
  printf 'Deployment 集合不完整。期望：%s；实际：%s\n' \
    "${expected_deployments[*]}" "${deployments[*]}" >&2
  exit 1
fi

for deployment in "${expected_deployments[@]}"; do
  kubectl -n "${namespace}" rollout status "deployment/${deployment}" --timeout="${FLOWMESH_ROLLOUT_TIMEOUT:-5m}"
  images="$(kubectl -n "${namespace}" get "deployment/${deployment}" -o jsonpath='{range .spec.template.spec.containers[*]}{.image}{"\n"}{end}')"
  while IFS= read -r image; do
    [[ -n "${image}" ]] || continue
    if [[ "${image}" != *":${expected_image_tag}" ]]; then
      echo "Deployment ${deployment} 使用了非目标提交镜像：${image}" >&2
      exit 1
    fi
  done <<< "${images}"
done

kubectl -n "${namespace}" get pdb -l "${deployment_selector}" >/dev/null
kubectl -n "${namespace}" get hpa -l "${deployment_selector}" >/dev/null
kubectl -n "${namespace}" get networkpolicy -l "${deployment_selector}" >/dev/null
kubectl -n "${namespace}" get networkpolicy "${release}-flowmesh-gateway-ingress" >/dev/null
kubectl -n "${namespace}" get secret "${FLOWMESH_RUNTIME_SECRET_NAME:-flowmesh-runtime-secrets}" >/dev/null
kubectl -n "${namespace}" get secret "${FLOWMESH_BACKUP_SECRET_NAME:-flowmesh-backup-credentials}" >/dev/null

cronjob_json="$(kubectl -n "${namespace}" get "cronjob/${backup_cronjob}" -o json)"
CRONJOB_JSON="${cronjob_json}" ruby -e '
require "json"
cronjob = JSON.parse(ENV.fetch("CRONJOB_JSON"))
spec = cronjob.fetch("spec")
raise "备份 CronJob 未设置 concurrencyPolicy=Forbid" unless spec.fetch("concurrencyPolicy") == "Forbid"
job_spec = spec.fetch("jobTemplate").fetch("spec")
raise "备份 CronJob 未设置 activeDeadlineSeconds" unless job_spec.fetch("activeDeadlineSeconds", 0).to_i > 0
raise "备份 CronJob 未设置 backoffLimit" unless job_spec.fetch("backoffLimit", -1).to_i >= 0
puts "备份 CronJob 参数校验通过。"
'

if [[ "${expect_prometheus_rule}" == "true" ]]; then
  kubectl -n "${namespace}" get servicemonitor "${release}-flowmesh" >/dev/null
  kubectl -n "${namespace}" get prometheusrule "${release}-flowmesh" >/dev/null
fi

echo "FlowMesh Kubernetes 生产 smoke test 通过：namespace=${namespace}, release=${release}, imageTag=${expected_image_tag}"

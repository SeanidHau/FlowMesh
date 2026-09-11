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
retention_cronjob="${release}-flowmesh-retention"

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
  deployment_json="$(kubectl -n "${namespace}" get "deployment/${deployment}" -o json)"
  DEPLOYMENT_JSON="${deployment_json}" DEPLOYMENT_NAME="${deployment}" ruby -e '
require "json"
name = ENV.fetch("DEPLOYMENT_NAME")
spec = JSON.parse(ENV.fetch("DEPLOYMENT_JSON")).fetch("spec").fetch("template").fetch("spec")
raise "#{name} 必须关闭 ServiceAccount Token 自动挂载" unless spec.fetch("automountServiceAccountToken") == false
security = spec.fetch("securityContext")
raise "#{name} 必须使用非 root 用户" unless security.fetch("runAsNonRoot") == true
raise "#{name} 必须使用 RuntimeDefault seccomp" unless security.fetch("seccompProfile").fetch("type") == "RuntimeDefault"
spec.fetch("containers").each do |container|
  container_name = container.fetch("name")
  container_security = container.fetch("securityContext")
  raise "#{name}/#{container_name} 不得允许提权" unless container_security.fetch("allowPrivilegeEscalation") == false
  raise "#{name}/#{container_name} 必须使用只读根文件系统" unless container_security.fetch("readOnlyRootFilesystem") == true
  raise "#{name}/#{container_name} 必须丢弃全部 Linux capabilities" unless container_security.fetch("capabilities").fetch("drop").include?("ALL")
  resources = container.fetch("resources")
  %w[requests limits].each do |resource_type|
    values = resources.fetch(resource_type)
    %w[cpu memory].each do |resource_name|
      raise "#{name}/#{container_name} 缺少 #{resource_type}.#{resource_name}" unless values.key?(resource_name)
    end
  end
  %w[startupProbe readinessProbe livenessProbe].each do |probe|
    raise "#{name}/#{container_name} 缺少 #{probe}" unless container.key?(probe)
  end
end
container = spec.fetch("containers").first
env = container.fetch("env").each_with_object({}) do |entry, values|
  values[entry.fetch("name")] = entry["value"]
end
if name.end_with?("-gateway", "-iam")
  raise "#{name} 必须启用 Redis TLS" unless env["REDIS_SSL_ENABLED"] == "true"
end
unless name.end_with?("-gateway")
  jdbc_url = env["SPRING_DATASOURCE_URL"].to_s
  raise "#{name} 必须使用 PostgreSQL 加密连接" unless jdbc_url.match?(/(?:\?|&)sslmode=(require|verify-ca|verify-full)(?:&|$)/)
end
rocketmq_services = %w[-supplier -workflow -risk -notification-audit]
if rocketmq_services.any? { |suffix| name.end_with?(suffix) }
  raise "#{name} 缺少 RocketMQ 访问通道" if env["ROCKETMQ_ACCESS_CHANNEL"].to_s.empty?
  raise "#{name} 必须启用 RocketMQ Consumer TLS" unless env["ROCKETMQ_CONSUMER_TLS_ENABLED"] == "true"
end
if %w[-supplier -workflow -risk].any? { |suffix| name.end_with?(suffix) }
  raise "#{name} 必须启用 RocketMQ Producer TLS" unless env["ROCKETMQ_PRODUCER_TLS_ENABLED"] == "true"
end
'
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
gateway_policy_json="$(kubectl -n "${namespace}" get networkpolicy "${release}-flowmesh-gateway-ingress" -o json)"
FLOWMESH_GATEWAY_POLICY_JSON="${gateway_policy_json}" EXPECTED_INGRESS_NAMESPACE="${FLOWMESH_INGRESS_NAMESPACE:-ingress-nginx}" ruby -e '
require "json"
policy = JSON.parse(ENV.fetch("FLOWMESH_GATEWAY_POLICY_JSON"))
expected = ENV.fetch("EXPECTED_INGRESS_NAMESPACE")
allowed = policy.fetch("spec").fetch("ingress").any? do |rule|
  rule.fetch("from", []).any? do |source|
    source.dig("namespaceSelector", "matchLabels", "kubernetes.io/metadata.name") == expected
  end
end
raise "Gateway NetworkPolicy 未允许指定的 Ingress Controller 命名空间" unless allowed
'

runtime_secret_json="$(kubectl -n "${namespace}" get secret "${FLOWMESH_RUNTIME_SECRET_NAME:-flowmesh-runtime-secrets}" -o json)"
RUNTIME_SECRET_JSON="${runtime_secret_json}" ruby -e '
require "json"
keys = JSON.parse(ENV.fetch("RUNTIME_SECRET_JSON")).fetch("data").keys
required = %w[JWT_SIGNING_KEY REDIS_PASSWORD IAM_DB_PASSWORD SUPPLIER_DB_PASSWORD WORKFLOW_DB_PASSWORD RISK_DB_PASSWORD AUDIT_DB_PASSWORD OBJECT_STORAGE_ACCESS_KEY OBJECT_STORAGE_SECRET_KEY ROCKETMQ_PRODUCER_ACCESS_KEY ROCKETMQ_PRODUCER_SECRET_KEY ROCKETMQ_CONSUMER_ACCESS_KEY ROCKETMQ_CONSUMER_SECRET_KEY]
missing = required - keys
raise "运行时 Secret 缺少键：#{missing.join(",")}" unless missing.empty?
'

backup_secret_json="$(kubectl -n "${namespace}" get secret "${FLOWMESH_BACKUP_SECRET_NAME:-flowmesh-backup-credentials}" -o json)"
BACKUP_SECRET_JSON="${backup_secret_json}" ruby -e '
require "json"
keys = JSON.parse(ENV.fetch("BACKUP_SECRET_JSON")).fetch("data").keys
raise "备份凭据 Secret 缺少 POSTGRES_PASSWORD" unless keys.include?("POSTGRES_PASSWORD")
'

retention_secret_json="$(kubectl -n "${namespace}" get secret "${FLOWMESH_RETENTION_SECRET_NAME:-flowmesh-retention-credentials}" -o json)"
RETENTION_SECRET_JSON="${retention_secret_json}" ruby -e '
require "json"
keys = JSON.parse(ENV.fetch("RETENTION_SECRET_JSON")).fetch("data").keys
raise "生命周期维护 Secret 缺少 RETENTION_DB_PASSWORD" unless keys.include?("RETENTION_DB_PASSWORD")
'

cronjob_json="$(kubectl -n "${namespace}" get "cronjob/${backup_cronjob}" -o json)"
CRONJOB_JSON="${cronjob_json}" ruby -e '
require "json"
cronjob = JSON.parse(ENV.fetch("CRONJOB_JSON"))
spec = cronjob.fetch("spec")
raise "备份 CronJob 未设置 concurrencyPolicy=Forbid" unless spec.fetch("concurrencyPolicy") == "Forbid"
job_spec = spec.fetch("jobTemplate").fetch("spec")
raise "备份 CronJob 未设置 activeDeadlineSeconds" unless job_spec.fetch("activeDeadlineSeconds", 0).to_i > 0
raise "备份 CronJob 未设置 backoffLimit" unless job_spec.fetch("backoffLimit", -1).to_i >= 0
container = job_spec.fetch("template").fetch("spec").fetch("containers").first
ssl_mode = container.fetch("env").find { |entry| entry.fetch("name") == "FLOWMESH_PG_SSLMODE" }
raise "备份 CronJob 未设置 PostgreSQL SSL 模式" unless ssl_mode && ssl_mode.fetch("value") != "disable"
puts "备份 CronJob 参数校验通过。"
'

retention_cronjob_json="$(kubectl -n "${namespace}" get "cronjob/${retention_cronjob}" -o json)"
RETENTION_CRONJOB_JSON="${retention_cronjob_json}" ruby -e '
require "json"
cronjob = JSON.parse(ENV.fetch("RETENTION_CRONJOB_JSON"))
spec = cronjob.fetch("spec")
raise "生命周期清理 CronJob 未设置 concurrencyPolicy=Forbid" unless spec.fetch("concurrencyPolicy") == "Forbid"
job_spec = spec.fetch("jobTemplate").fetch("spec")
pod = job_spec.fetch("template").fetch("spec")
raise "生命周期清理 CronJob 不得自动挂载 ServiceAccount Token" unless pod.fetch("automountServiceAccountToken") == false
container = pod.fetch("containers").first
env = container.fetch("env").to_h { |entry| [entry.fetch("name"), entry.fetch("value", nil)] }
raise "生命周期清理必须使用 YES 确认值" unless env.fetch("FLOWMESH_RETENTION_CONFIRM") == "YES"
raise "生命周期清理必须使用 PostgreSQL TLS" unless env.fetch("FLOWMESH_PG_SSLMODE") == "require"
puts "生命周期清理 CronJob 参数校验通过。"
'

if [[ "${expect_prometheus_rule}" == "true" ]]; then
  kubectl -n "${namespace}" get servicemonitor "${release}-flowmesh" >/dev/null
  kubectl -n "${namespace}" get prometheusrule "${release}-flowmesh" >/dev/null
fi

echo "FlowMesh Kubernetes 生产 smoke test 通过：namespace=${namespace}, release=${release}, imageTag=${expected_image_tag}"

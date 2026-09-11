#!/usr/bin/env bash
# 作用：验证强依赖基础设施已纳入对应服务的 Kubernetes readiness 检查。
# 该契约只读取配置，不启动服务或连接外部基础设施。

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
gateway_config="${repo_root}/services/gateway-service/src/main/resources/application.yml"
iam_config="${repo_root}/services/iam-service/src/main/resources/application.yml"
gateway_production_config="${repo_root}/services/gateway-service/src/main/resources/application-production.yml"
iam_production_config="${repo_root}/services/iam-service/src/main/resources/application-production.yml"
gateway_template="${repo_root}/infra/helm/flowmesh/templates/gateway.yaml"
iam_template="${repo_root}/infra/helm/flowmesh/templates/iam.yaml"

for file in "${gateway_config}" "${iam_config}" "${gateway_production_config}" \
  "${iam_production_config}" "${gateway_template}" "${iam_template}"; do
  [[ -s "${file}" ]] || {
    printf 'readiness 配置文件不存在或为空：%s\n' "${file}" >&2
    exit 1
  }
done

grep -F -- 'include: readinessState' "${gateway_config}" >/dev/null || {
  echo 'Gateway 默认 readiness 必须保留 readinessState。' >&2
  exit 1
}

grep -F -- 'include: readinessState,db' "${iam_config}" >/dev/null || {
  echo 'IAM 默认 readiness 必须保留 readinessState 和 db。' >&2
  exit 1
}

for file in "${gateway_production_config}" "${iam_production_config}"; do
  grep -F -- 'enabled: true' "${file}" >/dev/null || {
    echo "生产 profile 必须启用 Redis health indicator：${file}" >&2
    exit 1
  }
  grep -F -- 'include: readinessState,redis' "${file}" >/dev/null ||
    grep -F -- 'include: readinessState,db,redis' "${file}" >/dev/null || {
      echo "生产 profile readiness 必须包含 redis：${file}" >&2
      exit 1
    }
done

for file in "${gateway_template}" "${iam_template}"; do
  grep -F -- 'name: SPRING_PROFILES_ACTIVE' "${file}" >/dev/null || {
    echo "生产 Helm 模板必须激活 production profile：${file}" >&2
    exit 1
  }
  grep -F -- 'value: production' "${file}" >/dev/null || {
    echo "生产 Helm 模板必须使用 production profile：${file}" >&2
    exit 1
  }
done

echo 'Readiness dependency contract passed.'

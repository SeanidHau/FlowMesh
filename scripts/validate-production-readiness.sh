#!/usr/bin/env bash
# 作用：在不启动 Docker、不连接外部基础设施的前提下，复现仓库内全部生产化静态门禁和契约测试。
# 该脚本只能证明仓库自检通过；真实目标集群的 HA、恢复、压测和告警证据仍需单独执行。

set -Eeuo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo '校验 Shell 脚本语法……'
bash -n \
  "${root_dir}"/scripts/*.sh \
  "${root_dir}"/infra/backup/entrypoint.sh \
  "${root_dir}"/infra/retention/entrypoint.sh \
  "${root_dir}"/tests/fault-drills/*.sh \
  "${root_dir}"/tests/*.sh

echo '校验观测配置……'
bash "${root_dir}/scripts/validate-observability.sh"

echo '校验镜像供应链策略……'
bash "${root_dir}/scripts/validate-supply-chain-policy.sh"

echo '校验生产配置门禁……'
bash "${root_dir}/scripts/validate-production-config.sh" \
  "${root_dir}/infra/helm/flowmesh/values-production.yaml"

echo '校验服务 readiness 依赖……'
bash "${root_dir}/tests/readiness-dependency-contract.sh"

echo '校验 Gateway 请求体边界……'
bash "${root_dir}/tests/gateway-request-size-contract.sh"

echo '校验生产副本拓扑分散……'
bash "${root_dir}/tests/production-topology-spread-contract.sh"

echo '校验生产观测资源……'
bash "${root_dir}/tests/production-observability-contract.sh"

echo '校验 Redis ACL 配置……'
bash "${root_dir}/tests/redis-acl-contract.sh"

while IFS= read -r contract; do
  echo "执行生产契约：${contract#"${root_dir}/"}"
  bash "${contract}"
done < <(find "${root_dir}/tests" -type f -name '*contract.sh' -print | sort)

echo 'Production readiness repository checks passed.'

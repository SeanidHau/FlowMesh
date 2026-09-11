#!/usr/bin/env bash
# 作用：离线验证本地和 Kubernetes 观测规则都声明相同的服务级 SLO。

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
compose_rules="${repo_root}/infra/compose/prometheus-rules.yml"
helm_rules="${repo_root}/infra/helm/flowmesh/templates/prometheusrule.yaml"

for file in "${compose_rules}" "${helm_rules}"; do
  for marker in \
    'flowmesh:slo:http_requests_total:rate5m' \
    'flowmesh:slo:http_requests_5xx:rate5m' \
    'flowmesh:slo:http_availability_ratio:5m' \
    'flowmesh:slo:http_latency_p95_seconds:5m' \
    'FlowMeshHttpAvailabilitySloViolation' \
    'FlowMeshHttpLatencySloViolation'; do
    grep -F -- "${marker}" "${file}" >/dev/null || {
      echo "${file} 缺少 SLO 配置：${marker}" >&2
      exit 1
    }
  done
done

grep -F -- '99.9%' "${repo_root}/docs/production-readiness.md" >/dev/null
grep -F -- 'P95' "${repo_root}/docs/runbook.md" >/dev/null

echo 'SLO observability contract passed.'

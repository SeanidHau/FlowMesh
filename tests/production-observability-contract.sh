#!/usr/bin/env bash
# 作用：验证生产 Helm 覆盖值默认生成可被 Prometheus Operator 发现的抓取和告警资源。
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
values_file="${repo_root}/infra/helm/flowmesh/values-production.yaml"

grep -Eq '^  serviceMonitor:[[:space:]]*$' "${values_file}"
grep -Eq '^    enabled:[[:space:]]*true[[:space:]]*$' "${values_file}"
grep -Eq '^      release:[[:space:]]*[^[:space:]]+' "${values_file}"
grep -Eq '^  prometheusRule:[[:space:]]*$' "${values_file}"
grep -Eq '^    enabled:[[:space:]]*true[[:space:]]*$' "${values_file}"

grep -F -- 'kind: ServiceMonitor' "${repo_root}/infra/helm/flowmesh/templates/servicemonitor.yaml" >/dev/null
grep -F -- 'kind: PrometheusRule' "${repo_root}/infra/helm/flowmesh/templates/prometheusrule.yaml" >/dev/null

echo 'Production observability contract passed.'

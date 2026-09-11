#!/usr/bin/env bash
# 作用：验证生产 Helm 必须强制多副本跨节点分布，避免节点故障同时击穿同一服务的全部副本。
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
values_file="${repo_root}/infra/helm/flowmesh/values-production.yaml"

grep -Eq '^  topologySpread:[[:space:]]*$' "${values_file}"
grep -Eq '^    whenUnsatisfiable:[[:space:]]*DoNotSchedule[[:space:]]*$' "${values_file}"

for template in gateway iam supplier workflow risk notification-audit; do
  file="${repo_root}/infra/helm/flowmesh/templates/${template}.yaml"
  grep -F -- 'whenUnsatisfiable: {{ .Values.global.topologySpread.whenUnsatisfiable }}' "${file}" >/dev/null
done

echo 'Production topology spread contract passed.'

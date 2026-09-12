#!/usr/bin/env bash
# 作用：验证生产出口 NetworkPolicy 和发布入口不会误放通全网默认路由。

set -Eeuo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
template="${repo_root}/infra/helm/flowmesh/templates/networkpolicy-egress.yaml"
deploy_script="${repo_root}/scripts/deploy-production.sh"

grep -F -- 'networkPolicy.egress.externalCidrs must not allow an unrestricted default route' "${template}" >/dev/null
grep -F -- 'FLOWMESH_NETWORK_POLICY_EXTERNAL_CIDRS 不得允许全网出口' "${deploy_script}" >/dev/null

echo 'NetworkPolicy egress contract passed.'

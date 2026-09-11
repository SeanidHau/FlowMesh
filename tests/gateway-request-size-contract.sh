#!/usr/bin/env bash
# 作用：验证 Gateway 每条业务路由都配置了入口请求体上限，防止后续改路由时遗漏资源保护。

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
config="${repo_root}/services/gateway-service/src/main/resources/application.yml"

assert_route_limit() {
  local route_id="$1"
  local expected_property="$2"
  local expected_default="$3"
  local route_block

  route_block="$(awk -v route_id="${route_id}" '
    $0 ~ "- id: " route_id "$" { capture=1 }
    capture { print }
    capture && $0 ~ /- id: / && $0 !~ "- id: " route_id "$" { exit }
  ' "${config}")"

  printf '%s\n' "${route_block}" | grep -F -- 'name: RequestSize' >/dev/null || {
    echo "Gateway 路由缺少 RequestSize 限制：${route_id}" >&2
    exit 1
  }
  printf '%s\n' "${route_block}" | grep -F -- "maxSize: \${${expected_property}:${expected_default}}" >/dev/null || {
    echo "Gateway 路由请求体限制配置异常：${route_id}" >&2
    exit 1
  }
}

assert_route_limit 'iam-service' 'FLOWMESH_GATEWAY_AUTH_MAX_REQUEST_SIZE' '1MB'
assert_route_limit 'supplier-service' 'FLOWMESH_GATEWAY_SUPPLIER_MAX_REQUEST_SIZE' '21MB'
assert_route_limit 'workflow-service' 'FLOWMESH_GATEWAY_WORKFLOW_MAX_REQUEST_SIZE' '1MB'
assert_route_limit 'notification-audit-service' 'FLOWMESH_GATEWAY_NOTIFICATION_MAX_REQUEST_SIZE' '1MB'

echo 'Gateway request size contract passed.'

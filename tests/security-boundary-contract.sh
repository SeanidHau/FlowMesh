#!/usr/bin/env bash
# 作用：离线验证各服务的 HTTP 安全配置没有意外扩大匿名访问或弱化运维边界。

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

assert_contains() {
  local file="$1"
  local expected="$2"
  if ! grep -F -- "$expected" "$file" >/dev/null; then
    echo "安全边界缺少配置：${file} -> ${expected}" >&2
    exit 1
  fi
}

iam_security="${repo_root}/services/iam-service/src/main/java/com/flowmesh/iam/config/SecurityConfiguration.java"
assert_contains "${iam_security}" '.requestMatchers("/api/v1/auth/**").permitAll()'
assert_contains "${iam_security}" '.anyRequest().authenticated()'

for service in supplier workflow notification-audit; do
  package_name="${service//-}"
  security_file="${repo_root}/services/${service}-service/src/main/java/com/flowmesh/${package_name}/config/SecurityConfiguration.java"
  assert_contains "${security_file}" '.requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()'
  assert_contains "${security_file}" '.anyRequest().authenticated()'
done

supplier_security="${repo_root}/services/supplier-service/src/main/java/com/flowmesh/supplier/config/SecurityConfiguration.java"
assert_contains "${supplier_security}" '.requestMatchers("/api/v1/operations/**").hasRole("OPERATIONS")'
assert_contains "${supplier_security}" '.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/supplier-applications")'
assert_contains "${supplier_security}" '.hasRole("APPLICANT")'

workflow_security="${repo_root}/services/workflow-service/src/main/java/com/flowmesh/workflow/config/SecurityConfiguration.java"
assert_contains "${workflow_security}" '.requestMatchers("/api/v1/operations/**").hasRole("OPERATIONS")'
assert_contains "${workflow_security}" '.requestMatchers("/internal/v1/reconciliation/**").hasRole("OPERATIONS")'

gateway_config="${repo_root}/services/gateway-service/src/main/resources/application.yml"
assert_contains "${gateway_config}" 'Path=/api/iam/**'
assert_contains "${gateway_config}" 'Path=/api/supplier/**'
assert_contains "${gateway_config}" 'Path=/api/workflow/**'
assert_contains "${gateway_config}" 'Path=/api/notification/**'
if grep -F -- 'Path=/internal/' "${gateway_config}" >/dev/null; then
  echo 'Gateway 不得暴露 /internal/ 路径。' >&2
  exit 1
fi

# 仅允许健康、信息和 Prometheus 端点匿名访问；其他服务不得新增 permitAll。
for security_file in \
  "${iam_security}" \
  "${repo_root}/services/supplier-service/src/main/java/com/flowmesh/supplier/config/SecurityConfiguration.java" \
  "${repo_root}/services/workflow-service/src/main/java/com/flowmesh/workflow/config/SecurityConfiguration.java" \
  "${repo_root}/services/notification-audit-service/src/main/java/com/flowmesh/notificationaudit/config/SecurityConfiguration.java"; do
  permit_count="$(grep -o '\.permitAll()' "${security_file}" | wc -l | tr -d ' ')"
  if [[ "${security_file}" == *"/iam-service/"* ]]; then
    [[ "${permit_count}" -eq 2 ]] || { echo "IAM 匿名放行规则数量异常：${security_file}" >&2; exit 1; }
  else
    [[ "${permit_count}" -eq 1 ]] || { echo "服务匿名放行规则数量异常：${security_file}" >&2; exit 1; }
  fi
done

echo 'Security boundary contract passed.'

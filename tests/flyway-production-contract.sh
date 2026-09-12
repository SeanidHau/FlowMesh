#!/usr/bin/env bash
# 作用：离线验证五个业务服务使用一致的 Flyway 生产安全策略。

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
services=(iam supplier workflow risk notification-audit)

for service in "${services[@]}"; do
  file="${repo_root}/services/${service}-service/src/main/resources/application.yml"
  test -s "${file}"
  for marker in \
    'user: ${' \
    'password: ${' \
    'validate-on-migrate: true' \
    'clean-disabled: true' \
    'out-of-order: false' \
    'baseline-on-migrate: false'; do
    grep -F -- "${marker}" "${file}" >/dev/null || {
      echo "${file} 缺少 Flyway 生产安全配置：${marker}" >&2
      exit 1
    }
  done
done

for service_and_role in \
  'iam:flowmesh_iam_migrator' \
  'supplier:flowmesh_supplier_migrator' \
  'workflow:flowmesh_workflow_migrator' \
  'risk:flowmesh_risk_migrator' \
  'notification-audit:flowmesh_audit_migrator'; do
  service="${service_and_role%%:*}"
  role="${service_and_role#*:}"
  file="${repo_root}/services/${service}-service/src/main/resources/application.yml"
  grep -F -- "${role}" "${file}" >/dev/null || {
    echo "${file} 未配置独立 Flyway 迁移账号：${role}" >&2
    exit 1
  }
done

init_script="${repo_root}/infra/compose/postgres/init/01-init-roles-schemas.sh"
for role in iam supplier workflow risk audit; do
  grep -F -- "flowmesh_${role}_migrator" "${init_script}" >/dev/null || {
    echo "Compose PostgreSQL 初始化脚本缺少迁移账号：${role}" >&2
    exit 1
  }
done
grep -F -- 'ALTER DEFAULT PRIVILEGES' "${init_script}" >/dev/null

migration_template="${repo_root}/infra/helm/flowmesh/templates/migrations.yaml"
test -s "${migration_template}"
grep -F -- 'migrations:' "${repo_root}/infra/helm/flowmesh/values-production.yaml" >/dev/null
grep -F -- 'helm.sh/hook": pre-install,pre-upgrade' "${migration_template}" >/dev/null
grep -F -- 'FLOWMESH_MIGRATION_MODE' "${migration_template}" >/dev/null
grep -F -- 'FLOWMESH_MIGRATION_PASSWORD' "${migration_template}" >/dev/null
grep -F -- 'flowmesh.migrationSecret' "${migration_template}" >/dev/null
grep -F -- 'migrationExistingSecret' "${repo_root}/infra/helm/flowmesh/values-production.yaml" >/dev/null
for service in iam supplier workflow risk notification-audit; do
  grep -F -- "(dict \"name\" \"${service}\"" "${migration_template}" >/dev/null || {
    echo "Helm 迁移 Job 缺少服务：${service}" >&2
    exit 1
  }
done

for service in iam supplier workflow risk notification-audit; do
  deployment="${repo_root}/infra/helm/flowmesh/templates/${service}.yaml"
  grep -F -- 'SPRING_FLYWAY_ENABLED' "${deployment}" >/dev/null || {
    echo "${deployment} 未显式关闭应用 Pod 内的 Flyway" >&2
    exit 1
  }
  if grep -E 'SPRING_FLYWAY_(USER|PASSWORD)' "${deployment}" >/dev/null; then
    echo "${deployment} 不得向应用 Pod 注入 Flyway 凭据" >&2
    exit 1
  fi
done

echo 'Flyway production contract passed.'

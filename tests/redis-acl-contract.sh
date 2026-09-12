#!/usr/bin/env bash
# 作用：验证 Gateway 和 IAM 的 Redis 连接同时支持 ACL 用户名与密码认证。
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

for application in gateway-service iam-service; do
  grep -F -- 'username: ${REDIS_USERNAME:}' \
    "${repo_root}/services/${application}/src/main/resources/application.yml" >/dev/null
done

for template in gateway iam; do
  grep -F -- 'name: REDIS_USERNAME' \
    "${repo_root}/infra/helm/flowmesh/templates/${template}.yaml" >/dev/null
done

grep -Eq '^  username:[[:space:]]*""[[:space:]]*$' \
  "${repo_root}/infra/helm/flowmesh/values.yaml"
grep -Eq '^  username:[[:space:]]*""[[:space:]]*$' \
  "${repo_root}/infra/helm/flowmesh/values-production.yaml"
grep -F -- 'REDIS_USERNAME: ${REDIS_USERNAME:-}' \
  "${repo_root}/infra/compose/docker-compose.yml" >/dev/null

echo 'Redis ACL contract passed.'

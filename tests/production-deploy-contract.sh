#!/usr/bin/env bash
# 作用：离线验证生产发布入口具备镜像校验、原子 Helm 发布和发布后 smoke 门禁。

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
script="${repo_root}/scripts/deploy-production.sh"

bash -n "${script}"
grep -F -- 'scripts/verify-flowmesh-images.sh' "${script}" >/dev/null
grep -F -- 'scripts/validate-production-config.sh' "${script}" >/dev/null
grep -F -- 'helm lint' "${script}" >/dev/null
grep -F -- 'helm upgrade --install' "${script}" >/dev/null
grep -F -- '--atomic' "${script}" >/dev/null
grep -F -- '--wait' "${script}" >/dev/null
grep -F -- '--wait-for-jobs' "${script}" >/dev/null
grep -F -- '--timeout' "${script}" >/dev/null
grep -F -- 'tests/kubernetes-production-smoke.sh' "${script}" >/dev/null
grep -F -- 'helm history' "${script}" >/dev/null
grep -F -- 'helm rollback' "${script}" >/dev/null
grep -F -- '--keep-history' "${script}" >/dev/null
grep -F -- '发布后 smoke 失败' "${script}" >/dev/null
grep -F -- 'global.existingSecret' "${script}" >/dev/null
grep -F -- 'FLOWMESH_MIGRATION_SECRET_NAME' "${script}" >/dev/null
grep -F -- 'global.migrationExistingSecret' "${script}" >/dev/null
grep -F -- 'FLOWMESH_IMAGE_TAG' "${script}" >/dev/null
grep -F -- 'FLOWMESH_WORKFLOW_SLA_IMAGE_DIGEST' "${script}" >/dev/null
grep -F -- 'workflowSla.imageDigest=' "${script}" >/dev/null
grep -F -- 'FLOWMESH_NETWORK_POLICY_EXTERNAL_CIDRS' "${script}" >/dev/null
grep -F -- 'FLOWMESH_BACKUP_POSTGRES_USER' "${script}" >/dev/null
grep -F -- 'backup.postgres.user=' "${script}" >/dev/null
grep -F -- 'FLOWMESH_POSTGRES_CA_SECRET_NAME' "${script}" >/dev/null
grep -F -- 'postgresql.caSecretName=' "${script}" >/dev/null
grep -F -- 'backup.postgres.caSecretName=' "${script}" >/dev/null
grep -F -- 'retention.postgres.caSecretName=' "${script}" >/dev/null
grep -F -- 'FLOWMESH_IMAGE_PULL_SECRET_NAME' "${script}" >/dev/null
grep -F -- 'global.imagePullSecrets[0].name=' "${script}" >/dev/null

for forbidden in \
  'global.jwtSigningKey' \
  'global.redisPassword' \
  'services.iam.dbPassword' \
  'services.supplier.dbPassword' \
  'services.workflow.dbPassword' \
  'services.risk.dbPassword' \
  'services.notificationAudit.dbPassword' \
  'objectStorage.secretKey'; do
  if grep -F -- "${forbidden}" "${script}" >/dev/null; then
    echo "生产发布入口不得通过 Helm 参数传递敏感凭据：${forbidden}" >&2
    exit 1
  fi
done

echo 'Production deployment contract passed.'

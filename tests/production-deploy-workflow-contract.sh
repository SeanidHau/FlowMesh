#!/usr/bin/env bash
# 作用：离线验证生产发布工作流必须经过审批环境、完整 SHA 校验和串行部署门禁。

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
workflow="${repo_root}/.github/workflows/production-deploy.yml"

[[ -f "${workflow}" ]] || {
  echo '缺少生产发布工作流。' >&2
  exit 1
}

grep -F -- 'workflow_dispatch:' "${workflow}" >/dev/null
grep -F -- 'environment: production' "${workflow}" >/dev/null
grep -F -- 'runs-on: [self-hosted, linux, flowmesh-production]' "${workflow}" >/dev/null
grep -F -- 'concurrency:' "${workflow}" >/dev/null
grep -F -- 'cancel-in-progress: false' "${workflow}" >/dev/null
grep -F -- 'actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02 # v4' "${workflow}" >/dev/null
grep -F -- 'if: always()' "${workflow}" >/dev/null
grep -F -- 'FLOWMESH_IMAGE_TAG:' "${workflow}" >/dev/null
grep -F -- 'FLOWMESH_WORKFLOW_SLA_IMAGE_DIGEST:' "${workflow}" >/dev/null
grep -F -- 'FLOWMESH_IMAGE_PULL_SECRET_NAME:' "${workflow}" >/dev/null
grep -F -- 'FLOWMESH_ALERTMANAGER_CONFIG_SECRET_NAME:' "${workflow}" >/dev/null
grep -F -- 'FLOWMESH_MIGRATION_SECRET_NAME:' "${workflow}" >/dev/null
grep -F -- 'FLOWMESH_BACKUP_POSTGRES_USER:' "${workflow}" >/dev/null
grep -F -- 'FLOWMESH_POSTGRES_CA_SECRET_NAME: ${{ vars.FLOWMESH_POSTGRES_CA_SECRET_NAME }}' "${workflow}" >/dev/null
grep -F -- './scripts/deploy-production.sh' "${workflow}" >/dev/null
grep -F -- 'GITHUB_REF' "${workflow}" >/dev/null
grep -F -- "refs/heads/main" "${workflow}" >/dev/null

if grep -E '^[[:space:]]*(push|pull_request):' "${workflow}" >/dev/null; then
  echo '生产发布工作流只能通过 workflow_dispatch 触发。' >&2
  exit 1
fi

for forbidden in \
  'JWT_SIGNING_KEY:' \
  'REDIS_PASSWORD:' \
  'POSTGRES_PASSWORD:' \
  'DB_PASSWORD:' \
  'SECRET_KEY:'; do
  if grep -F -- "${forbidden}" "${workflow}" >/dev/null; then
    echo "生产发布工作流不得注入运行时凭据：${forbidden}" >&2
    exit 1
  fi
done

if grep -vE '^[[:space:]]*#' "${workflow}" | grep -E 'kubectl[[:space:]]+(apply|delete|patch|rollout[[:space:]]+restart|scale)|helm[[:space:]]+(uninstall|rollback)' >/dev/null; then
  echo '生产发布工作流不得绕过统一发布脚本执行未审计的集群写操作。' >&2
  exit 1
fi

echo 'Production deployment workflow contract passed.'

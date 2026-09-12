#!/usr/bin/env bash
# 作用：离线验证生产验收工作流只允许手动、审批后的只读执行，不包含部署或绕过门禁。

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
workflow="${repo_root}/.github/workflows/production-acceptance.yml"

[[ -f "${workflow}" ]] || {
  echo '缺少生产验收工作流。' >&2
  exit 1
}

grep -F -- 'workflow_dispatch:' "${workflow}" >/dev/null
grep -F -- 'environment: production' "${workflow}" >/dev/null
grep -F -- 'runs-on: [self-hosted, linux, flowmesh-production]' "${workflow}" >/dev/null
grep -F -- 'actions/upload-artifact@v4' "${workflow}" >/dev/null
grep -F -- 'if: always()' "${workflow}" >/dev/null
grep -F -- 'FLOWMESH_IMAGE_TAG:' "${workflow}" >/dev/null
grep -F -- 'FLOWMESH_EVIDENCE_DIR:' "${workflow}" >/dev/null
grep -F -- 'FLOWMESH_EVIDENCE_ENVIRONMENT:' "${workflow}" >/dev/null
grep -F -- 'evidence_environment:' "${workflow}" >/dev/null
grep -F -- 'FLOWMESH_BACKUP_DB_USER:' "${workflow}" >/dev/null
grep -F -- 'FLOWMESH_BACKUP_DB_PASSWORD:' "${workflow}" >/dev/null
grep -F -- 'FLOWMESH_POSTGRES_CA_SECRET_NAME: ${{ vars.FLOWMESH_POSTGRES_CA_SECRET_NAME }}' "${workflow}" >/dev/null
grep -F -- 'expect_prometheus_rule:' "${workflow}" >/dev/null
grep -F -- 'type: choice' "${workflow}" >/dev/null
grep -F -- 'GITHUB_REF' "${workflow}" >/dev/null
grep -F -- "refs/heads/main" "${workflow}" >/dev/null
grep -F -- "FLOWMESH_REQUIRE_RUNTIME_OBSERVABILITY: 'true'" "${workflow}" >/dev/null
grep -F -- "FLOWMESH_REQUIRE_DEPENDENCY_HA: 'true'" "${workflow}" >/dev/null
grep -F -- "FLOWMESH_REQUIRE_PRODUCTION_EVIDENCE: 'true'" "${workflow}" >/dev/null
if grep -E '^[[:space:]]*(push|pull_request):' "${workflow}" >/dev/null; then
  echo '生产验收工作流只能通过 workflow_dispatch 触发。' >&2
  exit 1
fi
if grep -vE '^[[:space:]]*#' "${workflow}" | grep -E 'kubectl[[:space:]]+(apply|delete|patch|rollout[[:space:]]+restart|scale)|helm[[:space:]]+(install|upgrade|rollback|uninstall)|docker[[:space:]]' >/dev/null; then
  echo '生产验收工作流不得执行部署、切换或容器运行时写操作。' >&2
  exit 1
fi

echo 'Production acceptance workflow contract passed.'

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
grep -F -- 'expect_prometheus_rule:' "${workflow}" >/dev/null
grep -F -- 'type: choice' "${workflow}" >/dev/null
if grep -E '^[[:space:]]*(push|pull_request):' "${workflow}" >/dev/null; then
  echo '生产验收工作流只能通过 workflow_dispatch 触发。' >&2
  exit 1
fi
grep -F -- 'FLOWMESH_REQUIRE_RUNTIME_OBSERVABILITY' "${workflow}" >/dev/null && {
  echo '生产工作流不得提供运行时观测绕过变量。' >&2
  exit 1
} || true
grep -F -- 'FLOWMESH_REQUIRE_DEPENDENCY_HA' "${workflow}" >/dev/null && {
  echo '生产工作流不得提供依赖 HA 绕过变量。' >&2
  exit 1
} || true
grep -F -- 'FLOWMESH_REQUIRE_PRODUCTION_EVIDENCE' "${workflow}" >/dev/null && {
  echo '生产工作流不得提供生产证据绕过变量。' >&2
  exit 1
} || true

if grep -vE '^[[:space:]]*#' "${workflow}" | grep -E 'kubectl[[:space:]]+(apply|delete|patch|rollout[[:space:]]+restart|scale)|helm[[:space:]]+(install|upgrade|rollback|uninstall)|docker[[:space:]]' >/dev/null; then
  echo '生产验收工作流不得执行部署、切换或容器运行时写操作。' >&2
  exit 1
fi

echo 'Production acceptance workflow contract passed.'

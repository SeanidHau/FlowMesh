#!/usr/bin/env bash
# 作用：离线验证生产 Kubernetes 故障演练工作流必须经过审批、主分支和显式确认门禁。

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
workflow="${repo_root}/.github/workflows/production-recovery-drill.yml"

[[ -f "${workflow}" ]] || {
  echo '缺少生产恢复演练工作流。' >&2
  exit 1
}

grep -F -- 'workflow_dispatch:' "${workflow}" >/dev/null
grep -F -- 'environment: production' "${workflow}" >/dev/null
grep -F -- 'runs-on: [self-hosted, linux, flowmesh-production]' "${workflow}" >/dev/null
grep -F -- 'concurrency:' "${workflow}" >/dev/null
grep -F -- 'cancel-in-progress: false' "${workflow}" >/dev/null
grep -F -- 'FLOWMESH_K8S_CHAOS_CONFIRM:' "${workflow}" >/dev/null
grep -F -- 'FLOWMESH_DRILL_EXPECTED_RTO_SECONDS:' "${workflow}" >/dev/null
grep -F -- 'verify-kubernetes-service-recovery.sh' "${workflow}" >/dev/null
grep -F -- 'actions/upload-artifact@v4' "${workflow}" >/dev/null
grep -F -- 'if: always()' "${workflow}" >/dev/null
grep -F -- 'GITHUB_REF' "${workflow}" >/dev/null
grep -F -- 'refs/heads/main' "${workflow}" >/dev/null

if grep -E '^[[:space:]]*(push|pull_request):' "${workflow}" >/dev/null; then
  echo '生产恢复演练工作流只能通过 workflow_dispatch 触发。' >&2
  exit 1
fi
if grep -vE '^[[:space:]]*#' "${workflow}" | grep -E 'kubectl[[:space:]]+delete|docker[[:space:]]' >/dev/null; then
  echo '工作流不得绕过故障演练脚本直接执行破坏性操作。' >&2
  exit 1
fi

echo 'Production recovery drill workflow contract passed.'

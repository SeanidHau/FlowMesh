#!/usr/bin/env bash
# 作用：离线验证主 CI 工作流具备并发收敛和长任务超时保护。

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
workflow="${repo_root}/.github/workflows/ci.yml"

grep -F -- 'concurrency:' "${workflow}" >/dev/null
grep -F -- 'cancel-in-progress: true' "${workflow}" >/dev/null
grep -F -- 'timeout-minutes: 30' "${workflow}" >/dev/null
grep -F -- 'timeout-minutes: 20' "${workflow}" >/dev/null
grep -F -- 'timeout 15m ./tests/rocketmq-e2e.sh' "${workflow}" >/dev/null

echo 'CI workflow contract passed.'

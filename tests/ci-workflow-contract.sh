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
grep -F -- 'sigstore/cosign-installer@6f9f17788090df1f26f669e9d70d6ae9567deba6 # v4.1.2' "${workflow}" >/dev/null
grep -F -- "cosign-release: 'v3.1.3'" "${workflow}" >/dev/null

# 作用：拒绝使用可变的 GitHub Actions 标签，避免供应链依赖在未审查时被替换。
if rg -n --glob '*.yml' --glob '*.yaml' \
  '^[[:space:]]*uses:[[:space:]]+[^[:space:]]+@(v[0-9]|main|master|latest)' \
  "${repo_root}/.github/workflows" >/dev/null; then
  echo 'GitHub Actions 必须固定到完整提交 SHA。' >&2
  exit 1
fi

# 作用：确保每个只读工作流都不会把 GitHub Token 持久化到工作区 Git 配置。
checkout_count="$(rg -c --glob '*.yml' --glob '*.yaml' '^[[:space:]]*uses:[[:space:]]+actions/checkout@[0-9a-f]{40}' "${repo_root}/.github/workflows" | awk -F: '{total += $NF} END {print total + 0}')"
persist_count="$(rg -c --glob '*.yml' --glob '*.yaml' '^[[:space:]]*persist-credentials:[[:space:]]+false[[:space:]]*$' "${repo_root}/.github/workflows" | awk -F: '{total += $NF} END {print total + 0}')"
if [[ "${checkout_count}" -eq 0 || "${checkout_count}" -ne "${persist_count}" ]]; then
  echo '每个 actions/checkout 都必须显式设置 persist-credentials: false。' >&2
  exit 1
fi

echo 'CI workflow contract passed.'

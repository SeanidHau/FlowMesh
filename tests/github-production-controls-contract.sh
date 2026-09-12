#!/usr/bin/env bash
# 作用：离线验证 GitHub 生产控制面预检只读检查了环境保护和主分支保护，不包含任何变更 API。

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
script="${repo_root}/scripts/validate-github-production-controls.sh"

[[ -x "${script}" ]] || {
  echo 'GitHub 生产控制面预检脚本缺失或不可执行。' >&2
  exit 1
}

grep -F -- 'repos/${repository}/environments/${environment_name}' "${script}" >/dev/null
grep -F -- 'required_reviewers' "${script}" >/dev/null
grep -F -- 'deployment_branch_policy.protected_branches' "${script}" >/dev/null
grep -F -- 'repos/${repository}/branches/${branch}/protection' "${script}" >/dev/null
grep -F -- 'GH_TOKEN' "${script}" >/dev/null
grep -F -- 'required_pull_request_reviews.required_approving_review_count' "${script}" >/dev/null
grep -F -- '.required_status_checks.contexts' "${script}" >/dev/null
grep -F -- '.required_status_checks.strict' "${script}" >/dev/null
grep -F -- '.enforce_admins.enabled' "${script}" >/dev/null
grep -F -- '.allow_force_pushes.enabled' "${script}" >/dev/null
grep -F -- '.allow_deletions.enabled' "${script}" >/dev/null
grep -F -- '不会创建、修改或删除' "${script}" >/dev/null

if grep -vE '^[[:space:]]*#' "${script}" | grep -E 'gh[[:space:]]+api[^$]*[[:space:]](POST|PUT|PATCH|DELETE)|--method[[:space:]]+(POST|PUT|PATCH|DELETE)' >/dev/null; then
  echo 'GitHub 生产控制面预检不得调用 GitHub 变更 API。' >&2
  exit 1
fi

echo 'GitHub production controls contract passed.'

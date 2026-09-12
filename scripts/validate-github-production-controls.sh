#!/usr/bin/env bash
# 作用：只读核验 GitHub 生产环境、分支保护和发布权限边界，阻止未受保护的仓库进入生产流程。
# 该脚本不会创建、修改或删除 GitHub 资源，也不会读取或输出任何 Secret 值。

set -Eeuo pipefail

require_command() {
  command -v "$1" >/dev/null || {
    printf 'GitHub 生产控制面预检缺少命令：%s\n' "$1" >&2
    exit 127
  }
}

require_command gh
require_command jq

[[ -n "${GH_TOKEN:-}" ]] || {
  printf 'GitHub 生产控制面预检需要显式提供 GH_TOKEN；请使用仅具备仓库 Administration 只读权限的 Token。\n' >&2
  exit 2
}

repository="${FLOWMESH_GITHUB_REPOSITORY:-${GITHUB_REPOSITORY:-}}"
if [[ -z "${repository}" ]]; then
  repository="$(gh repo view --json nameWithOwner --jq '.nameWithOwner')"
fi
branch="${FLOWMESH_GITHUB_BRANCH:-main}"
environment_name="${FLOWMESH_GITHUB_ENVIRONMENT:-production}"

[[ "${repository}" =~ ^[^/[:space:]]+/[^/[:space:]]+$ ]] || {
  printf 'GitHub 生产控制面预检要求 owner/repository，当前值无效。\n' >&2
  exit 2
}
[[ "${branch}" =~ ^[A-Za-z0-9._/-]+$ ]] || {
  printf 'GitHub 生产控制面预检收到无效分支名。\n' >&2
  exit 2
}
[[ "${environment_name}" =~ ^[^[:space:]/]+$ ]] || {
  printf 'GitHub 生产控制面预检收到无效 Environment 名称。\n' >&2
  exit 2
}

api_json() {
  gh api --header 'Accept: application/vnd.github+json' "$1"
}

environment_json="$(api_json "repos/${repository}/environments/${environment_name}")" || {
  printf 'GitHub Environment 读取失败：%s。请确认名称、权限和仓库配置。\n' "${environment_name}" >&2
  exit 1
}

protected_branch_policy="$(jq -r '.deployment_branch_policy.protected_branches // false' <<<"${environment_json}")"
required_reviewers="$(jq '[.protection_rules[]? | select(.type == "required_reviewers")] | length' <<<"${environment_json}")"
if [[ "${protected_branch_policy}" != 'true' || "${required_reviewers}" -lt 1 ]]; then
  printf 'GitHub Environment %s 未同时启用受保护分支策略和必需审核人。\n' "${environment_name}" >&2
  exit 1
fi

branch_protection_json="$(api_json "repos/${repository}/branches/${branch}/protection")" || {
  printf 'GitHub 分支保护读取失败：%s。请确认 %s 已配置分支保护。\n' "${branch}" "${branch}" >&2
  exit 1
}

required_approvals="$(jq -r '.required_pull_request_reviews.required_approving_review_count // 0' <<<"${branch_protection_json}")"
status_check_count="$(jq '.required_status_checks.contexts // [] | length' <<<"${branch_protection_json}")"
strict_status_checks="$(jq -r '.required_status_checks.strict // false' <<<"${branch_protection_json}")"
enforce_admins="$(jq -r '.enforce_admins.enabled // false' <<<"${branch_protection_json}")"
allow_force_pushes="$(jq -r '.allow_force_pushes.enabled // false' <<<"${branch_protection_json}")"
allow_deletions="$(jq -r '.allow_deletions.enabled // false' <<<"${branch_protection_json}")"

if [[ "${required_approvals}" -lt 1 ]]; then
  printf '分支 %s 未要求至少一名 Pull Request 审批人。\n' "${branch}" >&2
  exit 1
fi
if [[ "${status_check_count}" -lt 1 || "${strict_status_checks}" != 'true' ]]; then
  printf '分支 %s 未启用严格的必需状态检查。\n' "${branch}" >&2
  exit 1
fi
if [[ "${enforce_admins}" != 'true' ]]; then
  printf '分支 %s 未对管理员强制执行保护规则。\n' "${branch}" >&2
  exit 1
fi
if [[ "${allow_force_pushes}" == 'true' || "${allow_deletions}" == 'true' ]]; then
  printf '分支 %s 仍允许强制推送或删除。\n' "${branch}" >&2
  exit 1
fi

printf 'GitHub 生产控制面预检通过：Environment=%s，分支=%s，已启用审批、严格状态检查、管理员保护及不可强推/删除。\n' \
  "${environment_name}" "${branch}"

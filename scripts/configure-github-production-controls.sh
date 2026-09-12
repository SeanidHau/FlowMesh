#!/usr/bin/env bash
# 作用：以显式参数配置 GitHub 生产 Environment 和主分支保护。
# 默认只预览配置，不调用 GitHub 变更 API；只有 --apply 与二次确认同时存在时才会写入配置。

set -Eeuo pipefail

require_command() {
  command -v "$1" >/dev/null || {
    printf 'GitHub 生产控制面配置缺少命令：%s\n' "$1" >&2
    exit 127
  }
}

require_command gh
require_command jq

mode='dry-run'
case "${1:-}" in
  '') ;;
  --dry-run) mode='dry-run' ;;
  --apply) mode='apply' ;;
  *)
    printf '用法：%s [--dry-run|--apply]\n' "$0" >&2
    exit 2
    ;;
esac

[[ -n "${GH_TOKEN:-}" ]] || {
  printf 'GitHub 生产控制面配置需要显式提供 GH_TOKEN。\n' >&2
  exit 2
}

repository="${FLOWMESH_GITHUB_REPOSITORY:-${GITHUB_REPOSITORY:-}}"
if [[ -z "${repository}" ]]; then
  repository="$(gh repo view --json nameWithOwner --jq '.nameWithOwner')"
fi
branch="${FLOWMESH_GITHUB_BRANCH:-main}"
environment_name="${FLOWMESH_GITHUB_ENVIRONMENT:-production}"
reviewer_ids="${FLOWMESH_GITHUB_REVIEWER_IDS:-}"
status_checks="${FLOWMESH_GITHUB_REQUIRED_STATUS_CHECKS:-}"

[[ "${repository}" =~ ^[^/[:space:]]+/[^/[:space:]]+$ ]] || {
  printf 'GitHub 生产控制面配置要求 owner/repository，当前值无效。\n' >&2
  exit 2
}
[[ "${branch}" =~ ^[A-Za-z0-9._/-]+$ ]] || {
  printf 'GitHub 生产控制面配置收到无效分支名。\n' >&2
  exit 2
}
[[ "${environment_name}" =~ ^[^[:space:]/]+$ ]] || {
  printf 'GitHub 生产控制面配置收到无效 Environment 名称。\n' >&2
  exit 2
}
[[ -n "${reviewer_ids}" ]] || {
  printf '必须通过 FLOWMESH_GITHUB_REVIEWER_IDS 提供至少一个审核人，格式为 User:数字ID 或 Team:数字ID。\n' >&2
  exit 2
}
[[ -n "${status_checks}" ]] || {
  printf '必须通过 FLOWMESH_GITHUB_REQUIRED_STATUS_CHECKS 提供至少一个完整状态检查名称。\n' >&2
  exit 2
}

reviewers_json='[]'
IFS=',' read -r -a reviewer_entries <<<"${reviewer_ids}"
for reviewer_entry in "${reviewer_entries[@]}"; do
  reviewer_entry="${reviewer_entry#${reviewer_entry%%[![:space:]]*}}"
  reviewer_entry="${reviewer_entry%${reviewer_entry##*[![:space:]]}}"
  [[ "${reviewer_entry}" =~ ^(User|Team):([0-9]+)$ ]] || {
    printf '审核人值无效：%s；格式必须为 User:数字ID 或 Team:数字ID。\n' "${reviewer_entry}" >&2
    exit 2
  }
  reviewer_type="${BASH_REMATCH[1]}"
  reviewer_id="${BASH_REMATCH[2]}"
  reviewers_json="$(jq --arg type "${reviewer_type}" --argjson id "${reviewer_id}" '. + [{type: $type, id: $id}]' <<<"${reviewers_json}")"
done

contexts_json='[]'
IFS=',' read -r -a status_entries <<<"${status_checks}"
for status_entry in "${status_entries[@]}"; do
  status_entry="${status_entry#${status_entry%%[![:space:]]*}}"
  status_entry="${status_entry%${status_entry##*[![:space:]]}}"
  [[ -n "${status_entry}" ]] || {
    printf '状态检查名称不能为空。\n' >&2
    exit 2
  }
  contexts_json="$(jq --arg context "${status_entry}" '. + [$context]' <<<"${contexts_json}")"
done

environment_payload="$(jq -n \
  --argjson reviewers "${reviewers_json}" \
  '{wait_timer: 0, prevent_self_review: true, reviewers: $reviewers,
    deployment_branch_policy: {protected_branches: true, custom_branch_policies: false}}')"

branch_payload="$(jq -n \
  --argjson contexts "${contexts_json}" \
  '{required_status_checks: {strict: true, contexts: $contexts},
    enforce_admins: true,
    required_pull_request_reviews: {
      dismiss_stale_reviews: true,
      require_code_owner_reviews: true,
      required_approving_review_count: 1,
      require_last_push_approval: true
    },
    restrictions: null,
    required_linear_history: true,
    allow_force_pushes: false,
    allow_deletions: false,
    block_creations: false,
    required_conversation_resolution: true,
    lock_branch: false,
    allow_fork_syncing: false}')"

printf 'GitHub 生产控制面配置：仓库=%s，Environment=%s，分支=%s，审核人数=%s，状态检查数=%s。\n' \
  "${repository}" "${environment_name}" "${branch}" \
  "$(jq 'length' <<<"${reviewers_json}")" "$(jq 'length' <<<"${contexts_json}")"

if [[ "${mode}" == 'dry-run' ]]; then
  printf '当前为预览模式，未调用 GitHub 变更 API。使用 --apply 并设置 FLOWMESH_GITHUB_CONTROLS_CONFIRM=YES 才会应用。\n'
  exit 0
fi

[[ "${FLOWMESH_GITHUB_CONTROLS_CONFIRM:-}" == 'YES' ]] || {
  printf '应用 GitHub 生产控制面配置前，必须设置 FLOWMESH_GITHUB_CONTROLS_CONFIRM=YES。\n' >&2
  exit 2
}

temporary_directory="$(mktemp -d "${TMPDIR:-/tmp}/flowmesh-github-controls.XXXXXX")"
cleanup() {
  rm -rf -- "${temporary_directory}"
}
trap cleanup EXIT

printf '%s\n' "${environment_payload}" >"${temporary_directory}/environment.json"
printf '%s\n' "${branch_payload}" >"${temporary_directory}/branch.json"

gh api --method PUT --header 'Accept: application/vnd.github+json' \
  "repos/${repository}/environments/${environment_name}" \
  --input "${temporary_directory}/environment.json" >/dev/null

gh api --method PUT --header 'Accept: application/vnd.github+json' \
  "repos/${repository}/branches/${branch}/protection" \
  --input "${temporary_directory}/branch.json" >/dev/null

printf 'GitHub 生产控制面配置已应用。请立即运行 scripts/validate-github-production-controls.sh 复核。\n'

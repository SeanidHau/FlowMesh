#!/usr/bin/env bash
# 作用：离线验证 GitHub 生产控制面预检只读检查了环境保护和主分支保护，不包含任何变更 API。

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
script="${repo_root}/scripts/validate-github-production-controls.sh"
temporary_directory="$(mktemp -d "${TMPDIR:-/tmp}/flowmesh-github-controls-contract.XXXXXX")"
cleanup() {
  rm -rf -- "${temporary_directory}"
}
trap cleanup EXIT

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

fake_bin="${temporary_directory}/bin"
mkdir -p "${fake_bin}"
cat > "${fake_bin}/gh" <<'EOF'
#!/usr/bin/env bash
# 作用：为离线行为契约返回合规或不合规的只读 GitHub API 响应。
set -euo pipefail

endpoint="${!#}"
if [[ "${FAKE_GITHUB_CONTROLS_MODE:-pass}" == 'fail-environment' ]]; then
  printf '%s\n' '{"protection_rules":[],"deployment_branch_policy":{"protected_branches":false}}'
  exit 0
fi

case "${endpoint}" in
  repos/SeanidHau/FlowMesh/environments/production)
    printf '%s\n' '{"protection_rules":[{"type":"required_reviewers"}],"deployment_branch_policy":{"protected_branches":true}}'
    ;;
  repos/SeanidHau/FlowMesh/branches/main/protection)
    printf '%s\n' '{"required_pull_request_reviews":{"required_approving_review_count":1},"required_status_checks":{"contexts":["CI"],"strict":true},"enforce_admins":{"enabled":true},"allow_force_pushes":{"enabled":false},"allow_deletions":{"enabled":false}}'
    ;;
  *)
    echo "unexpected GitHub API endpoint: ${endpoint}" >&2
    exit 1
    ;;
esac
EOF
chmod +x "${fake_bin}/gh"

PATH="${fake_bin}:${PATH}" \
GH_TOKEN='contract-token' \
FLOWMESH_GITHUB_REPOSITORY='SeanidHau/FlowMesh' \
FLOWMESH_GITHUB_BRANCH='main' \
FLOWMESH_GITHUB_ENVIRONMENT='production' \
"${script}" >/dev/null

set +e
PATH="${fake_bin}:${PATH}" \
GH_TOKEN='contract-token' \
FAKE_GITHUB_CONTROLS_MODE='fail-environment' \
FLOWMESH_GITHUB_REPOSITORY='SeanidHau/FlowMesh' \
FLOWMESH_GITHUB_BRANCH='main' \
FLOWMESH_GITHUB_ENVIRONMENT='production' \
"${script}" >/dev/null 2>&1
result_code=$?
set -e
[[ "${result_code}" -ne 0 ]] || {
  echo 'GitHub 生产控制面预检未拒绝缺少审批的 Environment。' >&2
  exit 1
}

echo 'GitHub production controls contract passed.'

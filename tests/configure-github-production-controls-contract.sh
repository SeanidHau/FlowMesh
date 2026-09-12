#!/usr/bin/env bash
# 作用：验证 GitHub 生产控制面配置脚本默认只读，并且应用模式需要显式确认。

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
script="${repo_root}/scripts/configure-github-production-controls.sh"
temporary_directory="$(mktemp -d "${TMPDIR:-/tmp}/flowmesh-github-config-contract.XXXXXX")"
cleanup() {
  rm -rf -- "${temporary_directory}"
}
trap cleanup EXIT

[[ -x "${script}" ]] || {
  echo 'GitHub 生产控制面配置脚本缺失或不可执行。' >&2
  exit 1
}

grep -F -- '--apply' "${script}" >/dev/null
grep -F -- 'FLOWMESH_GITHUB_CONTROLS_CONFIRM' "${script}" >/dev/null
grep -F -- 'repos/${repository}/environments/${environment_name}' "${script}" >/dev/null
grep -F -- 'repos/${repository}/branches/${branch}/protection' "${script}" >/dev/null
grep -F -- 'required_status_checks' "${script}" >/dev/null
grep -F -- 'require_code_owner_reviews' "${script}" >/dev/null
grep -F -- 'allow_force_pushes: false' "${script}" >/dev/null
grep -F -- 'allow_deletions: false' "${script}" >/dev/null

fake_bin="${temporary_directory}/bin"
mkdir -p "${fake_bin}"
cat >"${fake_bin}/gh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >>"${FAKE_GH_LOG}"
if [[ "$1" == 'repo' ]]; then
  printf '%s\n' 'SeanidHau/FlowMesh'
fi
EOF
chmod +x "${fake_bin}/gh"

FAKE_GH_LOG="${temporary_directory}/gh.log" \
PATH="${fake_bin}:${PATH}" \
GH_TOKEN='contract-token' \
FLOWMESH_GITHUB_REPOSITORY='SeanidHau/FlowMesh' \
FLOWMESH_GITHUB_REVIEWER_IDS='User:123,Team:456' \
FLOWMESH_GITHUB_REQUIRED_STATUS_CHECKS='CI / Repository checks,Security / CodeQL (java-kotlin)' \
"${script}" --dry-run >/dev/null

[[ ! -s "${temporary_directory}/gh.log" ]] || {
  echo '默认预览模式不得调用 GitHub CLI。' >&2
  exit 1
}

set +e
FAKE_GH_LOG="${temporary_directory}/gh.log" \
PATH="${fake_bin}:${PATH}" \
GH_TOKEN='contract-token' \
FLOWMESH_GITHUB_REPOSITORY='SeanidHau/FlowMesh' \
FLOWMESH_GITHUB_REVIEWER_IDS='User:123' \
FLOWMESH_GITHUB_REQUIRED_STATUS_CHECKS='CI / Repository checks' \
"${script}" --apply >/dev/null 2>&1
result_code=$?
set -e
[[ "${result_code}" -eq 2 ]] || {
  echo '应用模式未拒绝缺少二次确认。' >&2
  exit 1
}
[[ ! -s "${temporary_directory}/gh.log" ]] || {
  echo '缺少二次确认时不得调用 GitHub CLI。' >&2
  exit 1
}

echo 'GitHub production controls configuration contract passed.'

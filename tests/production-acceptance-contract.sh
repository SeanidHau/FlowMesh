#!/usr/bin/env bash
# 作用：离线验证生产验收编排脚本覆盖完整检查集合，并拒绝覆盖已有证据。

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
script="${repo_root}/scripts/run-production-acceptance.sh"

bash -n "${script}"
for required_check in \
  'scripts/verify-flowmesh-images.sh' \
  'tests/kubernetes-production-smoke.sh' \
  'scripts/validate-production-dependencies.sh' \
  'scripts/validate-retention-role.sh'; do
  grep -F -- "${required_check}" "${script}" >/dev/null
done

grep -F -- 'FLOWMESH_ACCEPTANCE_REPORT' "${script}" >/dev/null
grep -F -- 'FLOWMESH_EXPECT_PROMETHEUS_RULE' "${script}" >/dev/null
grep -F -- 'FLOWMESH_REQUIRE_RUNTIME_OBSERVABILITY' "${script}" >/dev/null
grep -F -- 'scripts/validate-runtime-observability.sh' "${script}" >/dev/null
grep -F -- 'FLOWMESH_REQUIRE_DEPENDENCY_HA' "${script}" >/dev/null
grep -F -- 'scripts/validate-production-ha.sh' "${script}" >/dev/null
grep -F -- 'FLOWMESH_REQUIRE_PRODUCTION_EVIDENCE' "${script}" >/dev/null
grep -F -- 'scripts/validate-production-evidence.sh' "${script}" >/dev/null
grep -F -- 'FLOWMESH_REQUIRE_RUNTIME_OBSERVABILITY:-true' "${script}" >/dev/null
grep -F -- 'FLOWMESH_REQUIRE_DEPENDENCY_HA:-true' "${script}" >/dev/null
grep -F -- 'FLOWMESH_REQUIRE_PRODUCTION_EVIDENCE:-true' "${script}" >/dev/null
grep -F -- '拒绝覆盖已有验收报告' "${script}" >/dev/null
grep -F -- 'set -o noclobber' "${script}" >/dev/null
if grep -vE '^[[:space:]]*#' "${script}" | grep -E 'kubectl[[:space:]]+(apply|delete|patch|rollout[[:space:]]+restart|scale)' >/dev/null; then
  echo '生产验收编排脚本不得执行 Kubernetes 写操作。' >&2
  exit 1
fi

echo 'Production acceptance contract passed.'

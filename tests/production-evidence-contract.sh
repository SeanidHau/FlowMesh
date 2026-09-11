#!/usr/bin/env bash
# 作用：离线验证生产证据包校验的完整性、通过状态和敏感信息拦截边界。

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
script="${repo_root}/scripts/validate-production-evidence.sh"
temporary_directory="$(mktemp -d "${TMPDIR:-/tmp}/flowmesh-evidence-contract.XXXXXX")"
trap 'rm -rf -- "${temporary_directory}"' EXIT

bash -n "${script}"

required_files=(
  kubernetes-smoke.md
  dependency-ha.md
  runtime-observability.md
  service-recovery.md
  backup-restore.md
  load-test.md
  security-regression.md
  alert-routing.md
)

for file in "${required_files[@]}"; do
  cat > "${temporary_directory}/${file}" <<EOF
# ${file}

- 结果：\`PASS\`
EOF
done

cat > "${temporary_directory}/manifest.md" <<'EOF'
# FlowMesh 生产证据清单

- 环境标识：`contract-test`
- 执行人：`contract-test`
- 开始时间（UTC）：`2026-01-01T00:00:00Z`
- 完成时间（UTC）：`2026-01-01T00:01:00Z`
- 总结果：`PASS`
- kubernetes-smoke.md
- dependency-ha.md
- runtime-observability.md
- service-recovery.md
- backup-restore.md
- load-test.md
- security-regression.md
- alert-routing.md
EOF

(
  cd "${temporary_directory}"
  shasum -a 256 "${required_files[@]}" manifest.md > checksums.sha256
)

FLOWMESH_EVIDENCE_DIR="${temporary_directory}" "${script}" >/dev/null

if sed -i.bak 's/结果：`PASS`/结果：`FAIL`/' "${temporary_directory}/load-test.md"; then
  :
else
  perl -0pi -e 's/结果：`PASS`/结果：`FAIL`/' "${temporary_directory}/load-test.md"
fi
rm -f -- "${temporary_directory}/load-test.md.bak"
if FLOWMESH_EVIDENCE_DIR="${temporary_directory}" "${script}" >/dev/null 2>&1; then
  echo '证据包包含失败报告时应拒绝验收。' >&2
  exit 1
fi

sed -i.bak 's/结果：`FAIL`/结果：`PASS`/' "${temporary_directory}/load-test.md"
rm -f -- "${temporary_directory}/load-test.md.bak"
(
  cd "${temporary_directory}"
  shasum -a 256 "${required_files[@]}" manifest.md > checksums.sha256
)
printf '\nAuthorization: Bearer contract-secret\n' >> "${temporary_directory}/alert-routing.md"
if FLOWMESH_EVIDENCE_DIR="${temporary_directory}" "${script}" >/dev/null 2>&1; then
  echo '证据包包含敏感令牌时应拒绝验收。' >&2
  exit 1
fi

echo 'Production evidence contract passed.'

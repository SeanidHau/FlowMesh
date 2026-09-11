#!/usr/bin/env bash
# 作用：离线验证生产证据包校验的完整性、通过状态和敏感信息拦截边界。

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
script="${repo_root}/scripts/validate-production-evidence.sh"
manifest_generator="${repo_root}/scripts/create-production-evidence-manifest.sh"
temporary_directory="$(mktemp -d "${TMPDIR:-/tmp}/flowmesh-evidence-contract.XXXXXX")"
trap 'rm -rf -- "${temporary_directory}"' EXIT

bash -n "${script}"
bash -n "${manifest_generator}"

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

- 证据摘要：contract test evidence
- 结果：\`PASS\`
EOF
done

cat >> "${temporary_directory}/kubernetes-smoke.md" <<'EOF'
- 检查命令：`tests/kubernetes-production-smoke.sh`
- FLOWMESH_IMAGE_TAG：`flowmesh-test-image`
- 镜像提交：`0123456789012345678901234567890123456789`
- Deployment：六个 FlowMesh Deployment 已通过检查。
EOF
cat >> "${temporary_directory}/dependency-ha.md" <<'EOF'
- 检查命令：`scripts/validate-production-ha.sh`
- PostgreSQL：主库和复制副本通过检查。
- Redis：主从拓扑通过检查。
- RocketMQ：NameServer TLS 端点通过检查。
- 对象存储：HTTPS 端点通过检查。
EOF
cat >> "${temporary_directory}/runtime-observability.md" <<'EOF'
- 检查命令：`scripts/validate-runtime-observability.sh`
- Prometheus：服务目标和告警规则通过检查。
- Alertmanager：就绪状态通过检查。
- FlowMeshNotificationDelivery：通知投递告警已加载。
EOF
cat >> "${temporary_directory}/service-recovery.md" <<'EOF'
- 检查命令：`tests/fault-drills/verify-service-recovery.sh`
- 健康检查：服务恢复后返回成功状态。
- RTO：恢复时间符合目标。
EOF
cat >> "${temporary_directory}/backup-restore.md" <<'EOF'
- 检查命令：`scripts/restore-postgres.sh`
- 恢复：归档已恢复并读取探针数据。
- RPO：数据丢失窗口符合目标。
EOF
cat >> "${temporary_directory}/load-test.md" <<'EOF'
- 检查命令：`tests/k6/supplier-onboarding.js`
- RPS：吞吐达到目标。
- P95：延迟符合目标。
EOF
cat >> "${temporary_directory}/security-regression.md" <<'EOF'
- 检查命令：跨 tenant 访问回归测试。
- RLS：跨租户数据不可见。
- 403：越权请求被拒绝。
EOF
cat >> "${temporary_directory}/alert-routing.md" <<'EOF'
- 检查命令：Alertmanager receiver 路由演练。
- 通知：告警触发和恢复通知均已收到。
EOF

FLOWMESH_EVIDENCE_DIR="${temporary_directory}" \
FLOWMESH_EVIDENCE_ENVIRONMENT=contract-test \
FLOWMESH_EVIDENCE_OPERATOR=contract-test \
FLOWMESH_IMAGE_TAG=flowmesh-test-image \
  "${manifest_generator}" >/dev/null

if FLOWMESH_EVIDENCE_DIR="${temporary_directory}" \
  FLOWMESH_EVIDENCE_ENVIRONMENT=contract-test \
  FLOWMESH_EVIDENCE_OPERATOR=contract-test \
  "${manifest_generator}" >/dev/null 2>&1; then
  echo '证据清单生成器不得覆盖已有清单。' >&2
  exit 1
fi

FLOWMESH_EVIDENCE_DIR="${temporary_directory}" "${script}" >/dev/null

cp "${temporary_directory}/manifest.md" "${temporary_directory}/custom-manifest.md"
(
  cd "${temporary_directory}"
  shasum -a 256 "${required_files[@]}" custom-manifest.md > checksums.sha256
)
FLOWMESH_EVIDENCE_DIR="${temporary_directory}" \
FLOWMESH_EVIDENCE_MANIFEST=custom-manifest.md \
  "${script}" >/dev/null
if FLOWMESH_EVIDENCE_DIR="${temporary_directory}" FLOWMESH_EVIDENCE_MANIFEST=../custom-manifest.md \
  "${script}" >/dev/null 2>&1; then
  echo '证据清单不得通过路径穿越访问。' >&2
  exit 1
fi

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

#!/usr/bin/env bash
# 作用：为已由目标平台生成的生产证据报告创建清单和 SHA-256 校验和。
# 脚本不会生成或修改任何演练报告；缺少报告、报告未通过校验或包含敏感信息时直接失败。

set -Eeuo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
evidence_directory="${FLOWMESH_EVIDENCE_DIR:-}"
environment_name="${FLOWMESH_EVIDENCE_ENVIRONMENT:-}"
operator_name="${FLOWMESH_EVIDENCE_OPERATOR:-}"
image_tag="${FLOWMESH_IMAGE_TAG:-}"
manifest_name="${FLOWMESH_EVIDENCE_MANIFEST:-manifest.md}"
checksum_name="${FLOWMESH_EVIDENCE_CHECKSUMS:-checksums.sha256}"

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

require_value() {
  local name="$1"
  local value="$2"
  [[ -n "${value}" ]] || {
    printf '生产证据清单生成缺少环境变量：%s\n' "${name}" >&2
    exit 2
  }
}

validate_metadata() {
  local name="$1"
  local value="$2"
  if [[ "${value}" == *$'\n'* || "${value}" == *$'\r'* || "${value}" == *'`'* || "${value}" == *'PASSWORD='* \
    || "${value}" == *'SECRET_KEY='* || "${value}" == *'ACCESS_KEY='* || "${value}" == *'Bearer '* ]]; then
    printf '生产证据清单元数据包含非法或敏感内容：%s\n' "${name}" >&2
    exit 2
  fi
  if [[ "${#value}" -gt 256 ]]; then
    printf '生产证据清单元数据过长：%s\n' "${name}" >&2
    exit 2
  fi
}

require_value FLOWMESH_EVIDENCE_DIR "${evidence_directory}"
require_value FLOWMESH_EVIDENCE_ENVIRONMENT "${environment_name}"
require_value FLOWMESH_EVIDENCE_OPERATOR "${operator_name}"
require_value FLOWMESH_IMAGE_TAG "${image_tag}"
[[ -d "${evidence_directory}" ]] || {
  printf '生产证据目录不存在：%s\n' "${evidence_directory}" >&2
  exit 2
}

[[ "${manifest_name}" == "$(basename "${manifest_name}")" && "${manifest_name}" != '.' && "${manifest_name}" != '..' ]] || {
  echo 'FLOWMESH_EVIDENCE_MANIFEST 必须是证据目录内的文件名。' >&2
  exit 2
}
[[ "${checksum_name}" == "$(basename "${checksum_name}")" && "${checksum_name}" != '.' && "${checksum_name}" != '..' ]] || {
  echo 'FLOWMESH_EVIDENCE_CHECKSUMS 必须是证据目录内的文件名。' >&2
  exit 2
}

validate_metadata FLOWMESH_EVIDENCE_ENVIRONMENT "${environment_name}"
validate_metadata FLOWMESH_EVIDENCE_OPERATOR "${operator_name}"
validate_metadata FLOWMESH_IMAGE_TAG "${image_tag}"

manifest_file="${evidence_directory}/${manifest_name}"
checksum_file="${evidence_directory}/${checksum_name}"
[[ ! -e "${manifest_file}" && ! -e "${checksum_file}" ]] || {
  echo '拒绝覆盖已有生产证据清单或校验和。' >&2
  exit 64
}

for file in "${required_files[@]}"; do
  [[ -f "${evidence_directory}/${file}" && ! -L "${evidence_directory}/${file}" \
    && -s "${evidence_directory}/${file}" ]] || {
    printf '缺少或为空的生产证据报告：%s\n' "${evidence_directory}/${file}" >&2
    exit 1
  }
done

if command -v sha256sum >/dev/null 2>&1; then
  checksum_tool=(sha256sum)
elif command -v shasum >/dev/null 2>&1; then
  checksum_tool=(shasum -a 256)
else
  echo '生产证据清单生成需要 sha256sum 或 shasum。' >&2
  exit 127
fi

temporary_directory="$(mktemp -d "${TMPDIR:-/tmp}/flowmesh-evidence-manifest.XXXXXX")"
cleanup() {
  rm -rf -- "${temporary_directory}"
}
trap cleanup EXIT

started_at="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
cat > "${temporary_directory}/${manifest_name}" <<EOF
# FlowMesh 生产证据清单

- 环境标识：\`${environment_name}\`
- 执行人：\`${operator_name}\`
- 镜像提交：\`${image_tag}\`
- 开始时间（UTC）：\`${started_at}\`
- 完成时间（UTC）：\`${started_at}\`
- 总结果：\`PASS\`

$(printf '%s\n' "${required_files[@]}")
EOF

if ! (
  set -o noclobber
  cat "${temporary_directory}/${manifest_name}" > "${manifest_file}"
); then
  echo '无法以不可覆盖方式创建生产证据清单。' >&2
  exit 64
fi

(
  cd "${evidence_directory}"
  "${checksum_tool[@]}" "${required_files[@]}" "${manifest_name}" > "${temporary_directory}/${checksum_name}"
) || {
  rm -f -- "${manifest_file}"
  echo '无法生成生产证据校验和。' >&2
  exit 1
}

if ! (
  set -o noclobber
  cat "${temporary_directory}/${checksum_name}" > "${checksum_file}"
); then
  rm -f -- "${manifest_file}" "${checksum_file}"
  echo '无法以不可覆盖方式创建生产证据清单。' >&2
  exit 64
fi

if ! FLOWMESH_EVIDENCE_DIR="${evidence_directory}" \
  FLOWMESH_EVIDENCE_MANIFEST="${manifest_name}" \
  FLOWMESH_EVIDENCE_CHECKSUMS="${checksum_name}" \
  FLOWMESH_EVIDENCE_ENVIRONMENT="${environment_name}" \
  FLOWMESH_IMAGE_TAG="${image_tag}" \
  bash "${repo_root}/scripts/validate-production-evidence.sh"; then
  rm -f -- "${manifest_file}" "${checksum_file}"
  echo '生产证据报告未通过完整校验，已移除本次生成的清单和校验和。' >&2
  exit 1
fi

echo "生产证据清单已创建：${manifest_file}"
echo "生产证据校验和已创建：${checksum_file}"

#!/usr/bin/env bash
# 作用：只读校验目标生产环境的证据包是否完整、未被篡改且明确通过。
# 该脚本不生成、不修改、不删除证据；证据必须由目标平台实际演练后在隔离目录中归档。

set -Eeuo pipefail

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    printf '生产证据包校验缺少命令：%s\n' "$1" >&2
    exit 127
  }
}

require_value() {
  local name="$1"
  local value="$2"
  [[ -n "${value}" ]] || {
    printf '生产证据包校验缺少环境变量：%s\n' "${name}" >&2
    exit 2
  }
}

evidence_directory="${FLOWMESH_EVIDENCE_DIR:-}"
require_value FLOWMESH_EVIDENCE_DIR "${evidence_directory}"
expected_image_tag="${FLOWMESH_IMAGE_TAG:-}"
require_value FLOWMESH_IMAGE_TAG "${expected_image_tag}"
if [[ ! "${expected_image_tag}" =~ ^[0-9a-f]{40}$ ]]; then
  printf 'FLOWMESH_IMAGE_TAG 必须是 40 位小写 Git 提交 SHA：%s\n' "${expected_image_tag}" >&2
  exit 2
fi
[[ -d "${evidence_directory}" ]] || {
  printf '生产证据目录不存在：%s\n' "${evidence_directory}" >&2
  exit 2
}

manifest_name="${FLOWMESH_EVIDENCE_MANIFEST:-manifest.md}"
checksum_name="${FLOWMESH_EVIDENCE_CHECKSUMS:-checksums.sha256}"
[[ "${manifest_name}" == "$(basename "${manifest_name}")" && "${manifest_name}" != '.' && "${manifest_name}" != '..' ]] || {
  echo 'FLOWMESH_EVIDENCE_MANIFEST 必须是证据目录内的文件名。' >&2
  exit 2
}
[[ "${checksum_name}" == "$(basename "${checksum_name}")" && "${checksum_name}" != '.' && "${checksum_name}" != '..' ]] || {
  echo 'FLOWMESH_EVIDENCE_CHECKSUMS 必须是证据目录内的文件名。' >&2
  exit 2
}
manifest_file="${evidence_directory}/${manifest_name}"
checksum_file="${evidence_directory}/${checksum_name}"
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

require_command ruby
if command -v sha256sum >/dev/null 2>&1; then
  checksum_command=(sha256sum -c)
elif command -v shasum >/dev/null 2>&1; then
  checksum_command=(shasum -a 256 -c)
else
  echo '生产证据包校验需要 sha256sum 或 shasum。' >&2
  exit 127
fi

[[ -f "${manifest_file}" ]] || {
  printf '缺少生产证据清单：%s\n' "${manifest_file}" >&2
  exit 1
}
[[ -f "${checksum_file}" ]] || {
  printf '缺少生产证据校验和：%s\n' "${checksum_file}" >&2
  exit 1
}

for file in "${required_files[@]}"; do
  evidence_file="${evidence_directory}/${file}"
  [[ -s "${evidence_file}" ]] || {
    printf '缺少或为空的生产证据：%s\n' "${evidence_file}" >&2
    exit 1
  }
  grep -F -- '- 结果：`PASS`' "${evidence_file}" >/dev/null || {
    printf '生产证据未明确记录 PASS：%s\n' "${evidence_file}" >&2
    exit 1
  }
  grep -F -- '- 证据摘要：' "${evidence_file}" >/dev/null || {
    printf '生产证据缺少证据摘要：%s\n' "${evidence_file}" >&2
    exit 1
  }
  case "${file}" in
    kubernetes-smoke.md)
      required_markers=('tests/kubernetes-production-smoke.sh' 'FLOWMESH_IMAGE_TAG' 'Deployment')
      ;;
    dependency-ha.md)
      required_markers=('scripts/validate-production-ha.sh' 'PostgreSQL' 'Redis' 'RocketMQ' '对象存储')
      ;;
    runtime-observability.md)
      required_markers=('scripts/validate-runtime-observability.sh' 'Prometheus' 'Alertmanager' 'FlowMeshNotificationDelivery')
      ;;
    service-recovery.md)
      required_markers=('tests/fault-drills/verify-service-recovery.sh' 'RTO' '健康检查')
      ;;
    backup-restore.md)
      required_markers=('scripts/restore-postgres.sh' 'RPO' '恢复')
      ;;
    load-test.md)
      required_markers=('tests/k6/supplier-onboarding.js' 'RPS' 'P95')
      ;;
    security-regression.md)
      required_markers=('tenant' 'RLS' '403')
      ;;
    alert-routing.md)
      required_markers=('Alertmanager' 'receiver' '通知')
      ;;
  esac
  for marker in "${required_markers[@]}"; do
    grep -F -- "${marker}" "${evidence_file}" >/dev/null || {
      printf '生产证据缺少 %s 所需内容：%s\n' "${marker}" "${evidence_file}" >&2
      exit 1
    }
  done
done

grep -F -- '- 环境标识：' "${manifest_file}" >/dev/null || {
  echo '生产证据清单缺少环境标识。' >&2
  exit 1
}
grep -F -- '- 执行人：' "${manifest_file}" >/dev/null || {
  echo '生产证据清单缺少执行人。' >&2
  exit 1
}
grep -F -- '- 开始时间（UTC）：' "${manifest_file}" >/dev/null || {
  echo '生产证据清单缺少开始时间。' >&2
  exit 1
}
grep -F -- '- 完成时间（UTC）：' "${manifest_file}" >/dev/null || {
  echo '生产证据清单缺少完成时间。' >&2
  exit 1
}
grep -F -- '- 总结果：`PASS`' "${manifest_file}" >/dev/null || {
  echo '生产证据清单未明确记录 PASS。' >&2
  exit 1
}

MANIFEST_FILE="${manifest_file}" EXPECTED_IMAGE_TAG="${expected_image_tag}" ruby -rjson -e '
  content = File.read(ENV.fetch("MANIFEST_FILE"))
  expected_image_tag = ENV.fetch("EXPECTED_IMAGE_TAG")
  required = {
    "环境标识" => /- 环境标识：`[^`\n]+`/,
    "执行人" => /- 执行人：`[^`\n]+`/,
    "镜像提交" => /- 镜像提交：`#{Regexp.escape(expected_image_tag)}`/,
    "开始时间" => /- 开始时间（UTC）：`[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z`/,
    "完成时间" => /- 完成时间（UTC）：`[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z`/
  }
  required.each do |name, pattern|
    abort "生产证据清单的 #{name} 格式无效。" unless content.match?(pattern)
  end
'

for file in "${required_files[@]}"; do
  grep -F -- "${file}" "${manifest_file}" >/dev/null || {
    printf '生产证据清单未列出必需报告：%s\n' "${file}" >&2
    exit 1
  }
done

CHECKSUM_FILE="${checksum_file}" EXPECTED_MANIFEST_FILE="${manifest_name}" ruby -e '
  expected = %w[kubernetes-smoke.md dependency-ha.md runtime-observability.md service-recovery.md backup-restore.md load-test.md security-regression.md alert-routing.md].push(ENV.fetch("EXPECTED_MANIFEST_FILE")).sort
  actual = File.readlines(ENV.fetch("CHECKSUM_FILE"), chomp: true).reject(&:empty?).map do |line|
    parts = line.split(/\s+/, 2)
    abort "生产证据校验和格式无效。" unless parts.length == 2 && parts[0].match?(/\A[0-9a-fA-F]{64}\z/)
    path = parts[1].sub(/\A\*/, "")
    abort "生产证据校验和包含非法路径：#{path}" if path.start_with?("/") || path.include?("..")
    path
  end.sort
  abort "生产证据校验和文件集合不完整。" unless actual == expected
'

(
  cd "${evidence_directory}"
  "${checksum_command[@]}" "$(basename "${checksum_file}")"
) >/dev/null

# 作用：阻止把口令、令牌或私钥意外归档到生产证据目录。
if grep -R -n -E 'BEGIN (RSA|OPENSSH|EC|PRIVATE) KEY|Authorization:[[:space:]]*Bearer|PASSWORD[=:]|SECRET_KEY[=:]|ACCESS_KEY[=:]' \
  "${manifest_file}" "${checksum_file}" "${required_files[@]/#/${evidence_directory}/}" >/dev/null 2>&1; then
  echo '生产证据包疑似包含敏感凭据，拒绝验收。' >&2
  exit 1
fi

echo "生产证据包校验通过：${evidence_directory}"

#!/usr/bin/env bash

# 作用：离线验证对象存储生命周期脚本的输入校验、HTTPS 门禁和 AWS CLI 调用参数。

set -Eeuo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
script="${repo_root}/scripts/configure-object-storage-lifecycle.sh"
temp_root="$(mktemp -d "${TMPDIR:-/tmp}/flowmesh-object-lifecycle-contract.XXXXXX")"
fake_bin="${temp_root}/bin"
aws_log="${temp_root}/aws.log"
mkdir -p "${fake_bin}"
trap 'rm -rf -- "${temp_root}"' EXIT

cat > "${fake_bin}/aws" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail
printf '%s\n' "$*" >> "${AWS_LOG:?}"
for argument in "$@"; do
  if [[ "${argument}" == file://* ]]; then
    cp "${argument#file://}" "${CAPTURED_POLICY:?}"
  fi
done
EOF
chmod +x "${fake_bin}/aws"

PATH="${fake_bin}:${PATH}" \
AWS_LOG="${aws_log}" \
CAPTURED_POLICY="${temp_root}/policy.json" \
FLOWMESH_OBJECT_STORAGE_BUCKET=flowmesh-documents \
FLOWMESH_OBJECT_STORAGE_ENDPOINT=https://object-storage.example.com \
FLOWMESH_OBJECT_STORAGE_NONCURRENT_RETENTION_DAYS=7 \
  "${script}" >/dev/null

grep -F -- 's3api put-bucket-versioning' "${aws_log}" >/dev/null
grep -F -- 'Status=Enabled' "${aws_log}" >/dev/null
grep -F -- 's3api put-bucket-lifecycle-configuration' "${aws_log}" >/dev/null
grep -F -- '--endpoint-url https://object-storage.example.com' "${aws_log}" >/dev/null
grep -F -- '"NoncurrentDays": 7' "${temp_root}/policy.json" >/dev/null
grep -F -- '"ExpiredObjectDeleteMarker": true' "${temp_root}/policy.json" >/dev/null

if PATH="${fake_bin}:${PATH}" \
  AWS_LOG="${aws_log}" \
  FLOWMESH_OBJECT_STORAGE_BUCKET=flowmesh-documents \
  FLOWMESH_OBJECT_STORAGE_ENDPOINT=http://object-storage.example.com \
    "${script}" >/dev/null 2>&1; then
  echo '对象存储生命周期脚本接受了非 HTTPS endpoint。' >&2
  exit 1
fi

if PATH="${fake_bin}:${PATH}" \
  AWS_LOG="${aws_log}" \
  FLOWMESH_OBJECT_STORAGE_BUCKET=flowmesh-documents \
  FLOWMESH_OBJECT_STORAGE_NONCURRENT_RETENTION_DAYS=0 \
    "${script}" >/dev/null 2>&1; then
  echo '对象存储生命周期脚本接受了非正数保留期。' >&2
  exit 1
fi

echo 'Object storage lifecycle contract passed.'

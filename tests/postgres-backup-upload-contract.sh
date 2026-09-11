#!/usr/bin/env bash
# 作用：离线验证 PostgreSQL 备份脚本的对象存储上传、服务端加密和失败清理分支。
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
temp_root="$(mktemp -d)"
fake_bin="${temp_root}/bin"
backup_root="${temp_root}/backups"
aws_log="${temp_root}/aws.log"
ssl_log="${temp_root}/sslmode.log"
remote_verify_log="${temp_root}/remote-verify.log"
mkdir -p "${fake_bin}"
trap 'rm -rf -- "${temp_root}"' EXIT

cat > "${fake_bin}/pg_dump" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "${PGSSLMODE:-}" >> "${SSL_LOG:?}"
for argument in "$@"; do
  case "${argument}" in
    --file=*) printf 'fake custom format backup\n' > "${argument#--file=}" ;;
  esac
done
EOF

cat > "${fake_bin}/pg_dumpall" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "${PGSSLMODE:-}" >> "${SSL_LOG:?}"
printf 'CREATE ROLE flowmesh;\n'
EOF

cat > "${fake_bin}/aws" <<EOF
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "\$*" >> "${aws_log}"
if [[ "\$1 \$2" == 's3api head-object' ]]; then
  printf '%s\n' "\$*" >> "${remote_verify_log}"
  if [[ "\${FLOWMESH_TEST_FAIL_REMOTE_VERIFY:-false}" == 'true' ]]; then
    exit 1
  fi
fi
EOF

chmod +x "${fake_bin}/pg_dump" "${fake_bin}/pg_dumpall" "${fake_bin}/aws"

PATH="${fake_bin}:${PATH}" \
FLOWMESH_PG_PASSWORD=test \
FLOWMESH_BACKUP_ROOT="${backup_root}" \
FLOWMESH_BACKUP_S3_URI=s3://flowmesh-backups \
FLOWMESH_BACKUP_S3_SSE=aws:kms \
FLOWMESH_BACKUP_S3_KMS_KEY_ID=alias/flowmesh-backup \
FLOWMESH_BACKUP_S3_VERIFY_REMOTE=true \
FLOWMESH_BACKUP_CLEANUP_LOCAL=true \
FLOWMESH_PG_SSLMODE=require \
SSL_LOG="${ssl_log}" \
AWS_REGION=cn-shanghai \
  "${repo_root}/scripts/backup-postgres.sh" >/dev/null

grep -F -- '--sse aws:kms' "${aws_log}" >/dev/null
grep -F -- '--sse-kms-key-id alias/flowmesh-backup' "${aws_log}" >/dev/null
grep -F -- 's3://flowmesh-backups/' "${aws_log}" >/dev/null
grep -F -- '_SUCCESS' "${aws_log}" >/dev/null
[[ "$(wc -l < "${remote_verify_log}")" -eq 3 ]]
grep -Fx -- 'require' "${ssl_log}" >/dev/null
if find "${backup_root}" -mindepth 1 -print -quit | grep -q .; then
  echo '成功上传后未清理临时备份目录。' >&2
  exit 1
fi
success_uploads_before="$(grep -Fc -- '_SUCCESS' "${aws_log}" || true)"
aws_calls_before="$(wc -l < "${aws_log}")"

if PATH="${fake_bin}:${PATH}" \
  FLOWMESH_PG_PASSWORD=test \
  FLOWMESH_BACKUP_ROOT="${backup_root}" \
  FLOWMESH_BACKUP_S3_URI=s3://flowmesh-backups \
  FLOWMESH_BACKUP_S3_VERIFY_REMOTE=maybe \
  "${repo_root}/scripts/backup-postgres.sh" >/dev/null 2>&1; then
  echo '无效的远端校验开关未被拒绝。' >&2
  exit 1
fi
[[ "$(wc -l < "${aws_log}")" -eq "${aws_calls_before}" ]]

if PATH="${fake_bin}:${PATH}" \
  FLOWMESH_PG_PASSWORD=test \
  FLOWMESH_BACKUP_ROOT="${backup_root}" \
  FLOWMESH_BACKUP_S3_URI=not-an-s3-uri \
  FLOWMESH_BACKUP_S3_VERIFY_REMOTE=true \
  "${repo_root}/scripts/backup-postgres.sh" >/dev/null 2>&1; then
  echo '无效的 S3 URI 未被拒绝。' >&2
  exit 1
fi
[[ "$(wc -l < "${aws_log}")" -eq "${aws_calls_before}" ]]

if PATH="${fake_bin}:${PATH}" \
  FLOWMESH_PG_PASSWORD=test \
  FLOWMESH_BACKUP_ROOT="${backup_root}" \
  FLOWMESH_BACKUP_S3_URI=s3://flowmesh-backups \
  FLOWMESH_BACKUP_S3_VERIFY_REMOTE=true \
  FLOWMESH_TEST_FAIL_REMOTE_VERIFY=true \
  "${repo_root}/scripts/backup-postgres.sh" >/dev/null 2>&1; then
  echo '远端对象校验失败时备份脚本不应成功。' >&2
  exit 1
fi
success_uploads_after="$(grep -Fc -- '_SUCCESS' "${aws_log}" || true)"
if [[ "${success_uploads_after}" -ne "${success_uploads_before}" ]]; then
  echo '远端对象校验失败时不应上传 _SUCCESS 标记。' >&2
  exit 1
fi
if find "${backup_root}" -mindepth 1 -print -quit | grep -q .; then
  echo '远端对象校验失败后未清理部分备份目录。' >&2
  exit 1
fi

if PATH="${fake_bin}:${PATH}" \
  FLOWMESH_PG_PASSWORD=test \
  FLOWMESH_BACKUP_ROOT="${backup_root}" \
  FLOWMESH_BACKUP_S3_URI=s3://flowmesh-backups \
  FLOWMESH_BACKUP_S3_SSE=unsupported \
  "${repo_root}/scripts/backup-postgres.sh" >/dev/null 2>&1; then
  echo '不支持的服务端加密算法未被拒绝。' >&2
  exit 1
fi

if find "${backup_root}" -mindepth 1 -print -quit | grep -q .; then
  echo '上传配置失败后未清理部分备份目录。' >&2
  exit 1
fi

echo 'PostgreSQL backup upload contract passed.'

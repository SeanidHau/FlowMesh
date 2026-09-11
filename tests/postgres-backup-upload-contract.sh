#!/usr/bin/env bash
# 作用：离线验证 PostgreSQL 备份脚本的对象存储上传、服务端加密和失败清理分支。
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
temp_root="$(mktemp -d)"
fake_bin="${temp_root}/bin"
backup_root="${temp_root}/backups"
aws_log="${temp_root}/aws.log"
mkdir -p "${fake_bin}"
trap 'rm -rf -- "${temp_root}"' EXIT

cat > "${fake_bin}/pg_dump" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
for argument in "$@"; do
  case "${argument}" in
    --file=*) printf 'fake custom format backup\n' > "${argument#--file=}" ;;
  esac
done
EOF

cat > "${fake_bin}/pg_dumpall" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf 'CREATE ROLE flowmesh;\n'
EOF

cat > "${fake_bin}/aws" <<EOF
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "\$*" >> "${aws_log}"
EOF

chmod +x "${fake_bin}/pg_dump" "${fake_bin}/pg_dumpall" "${fake_bin}/aws"

PATH="${fake_bin}:${PATH}" \
FLOWMESH_PG_PASSWORD=test \
FLOWMESH_BACKUP_ROOT="${backup_root}" \
FLOWMESH_BACKUP_S3_URI=s3://flowmesh-backups \
FLOWMESH_BACKUP_S3_SSE=aws:kms \
FLOWMESH_BACKUP_S3_KMS_KEY_ID=alias/flowmesh-backup \
FLOWMESH_BACKUP_CLEANUP_LOCAL=true \
AWS_REGION=cn-shanghai \
  "${repo_root}/scripts/backup-postgres.sh" >/dev/null

grep -F -- '--sse aws:kms' "${aws_log}" >/dev/null
grep -F -- '--sse-kms-key-id alias/flowmesh-backup' "${aws_log}" >/dev/null
grep -F -- 's3://flowmesh-backups/' "${aws_log}" >/dev/null
grep -F -- '_SUCCESS' "${aws_log}" >/dev/null
if find "${backup_root}" -mindepth 1 -print -quit | grep -q .; then
  echo '成功上传后未清理临时备份目录。' >&2
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

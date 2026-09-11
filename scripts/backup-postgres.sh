#!/usr/bin/env bash
set -euo pipefail

# 作用：将 FlowMesh PostgreSQL 数据库和角色定义导出为可验证的备份目录。
# 密码只通过 FLOWMESH_PG_PASSWORD 注入，不出现在命令行参数中。

: "${FLOWMESH_PG_PASSWORD:?请设置 FLOWMESH_PG_PASSWORD}"

backup_root="${FLOWMESH_BACKUP_ROOT:-./backups/postgres}"
database="${FLOWMESH_PG_DATABASE:-flowmesh}"
host="${FLOWMESH_PG_HOST:-localhost}"
port="${FLOWMESH_PG_PORT:-5432}"
user="${FLOWMESH_PG_USER:-flowmesh}"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
backup_dir="${backup_root}/${timestamp}"

mkdir -p "${backup_root}"
chmod 700 "${backup_root}"
if ! mkdir "${backup_dir}"; then
  printf '备份目录已存在，拒绝覆盖：%s\n' "${backup_dir}" >&2
  exit 1
fi
chmod 700 "${backup_dir}"

cleanup_failed_backup() {
  local status=$?
  unset PGPASSWORD PGCONNECT_TIMEOUT
  if [[ "${status}" -ne 0 && -d "${backup_dir}" ]]; then
    rm -rf -- "${backup_dir}"
  fi
  exit "${status}"
}
trap cleanup_failed_backup EXIT

export PGPASSWORD="${FLOWMESH_PG_PASSWORD}"
export PGCONNECT_TIMEOUT="${FLOWMESH_PG_CONNECT_TIMEOUT_SECONDS:-5}"

pg_dump \
  --format=custom \
  --no-owner \
  --no-privileges \
  --file="${backup_dir}/flowmesh.dump" \
  --host="${host}" \
  --port="${port}" \
  --username="${user}" \
  "${database}"

pg_dumpall \
  --globals-only \
  --no-role-passwords \
  --host="${host}" \
  --port="${port}" \
  --username="${user}" \
  > "${backup_dir}/globals.sql"

if command -v sha256sum >/dev/null 2>&1; then
  (cd "${backup_dir}" && sha256sum flowmesh.dump globals.sql > checksums.sha256)
else
  (cd "${backup_dir}" && shasum -a 256 flowmesh.dump globals.sql > checksums.sha256)
fi

chmod 600 "${backup_dir}/flowmesh.dump" "${backup_dir}/globals.sql" "${backup_dir}/checksums.sha256"

if [[ -n "${FLOWMESH_BACKUP_S3_URI:-}" ]]; then
  # 作用：将已校验的备份文件逐个上传到 S3 兼容对象存储；上传失败会触发统一清理逻辑。
  command -v aws >/dev/null 2>&1 || {
    printf '配置了 FLOWMESH_BACKUP_S3_URI，但当前环境未安装 aws 命令。\n' >&2
    exit 1
  }

  destination="${FLOWMESH_BACKUP_S3_URI%/}/$(basename "${backup_dir}")"
  aws_arguments=(s3 cp --only-show-errors)
  if [[ -n "${FLOWMESH_BACKUP_S3_ENDPOINT:-}" ]]; then
    aws_arguments+=(--endpoint-url "${FLOWMESH_BACKUP_S3_ENDPOINT}")
  fi
  if [[ -n "${AWS_REGION:-}" ]]; then
    aws_arguments+=(--region "${AWS_REGION}")
  fi

  server_side_encryption="${FLOWMESH_BACKUP_S3_SSE:-AES256}"
  case "${server_side_encryption}" in
    ""|none)
      ;;
    AES256|aws:kms)
      aws_arguments+=(--sse "${server_side_encryption}")
      if [[ "${server_side_encryption}" == "aws:kms" && -n "${FLOWMESH_BACKUP_S3_KMS_KEY_ID:-}" ]]; then
        aws_arguments+=(--sse-kms-key-id "${FLOWMESH_BACKUP_S3_KMS_KEY_ID}")
      fi
      ;;
    *)
      printf '不支持的 FLOWMESH_BACKUP_S3_SSE：%s\n' "${server_side_encryption}" >&2
      exit 1
      ;;
  esac

  for backup_file in flowmesh.dump globals.sql checksums.sha256; do
    aws "${aws_arguments[@]}" \
      "${backup_dir}/${backup_file}" \
      "${destination}/${backup_file}"
  done
  printf 'completedAt=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "${backup_dir}/_SUCCESS"
  chmod 600 "${backup_dir}/_SUCCESS"
  aws "${aws_arguments[@]}" "${backup_dir}/_SUCCESS" "${destination}/_SUCCESS"
  printf 'PostgreSQL 备份已上传：%s\n' "${destination}"
  if [[ "${FLOWMESH_BACKUP_CLEANUP_LOCAL:-false}" == "true" ]]; then
    rm -rf -- "${backup_dir}"
  fi
fi

trap - EXIT
unset PGPASSWORD PGCONNECT_TIMEOUT

printf 'PostgreSQL 备份已创建：%s\n' "${backup_dir}"

#!/usr/bin/env bash
set -euo pipefail

# 作用：不连接目标数据库，仅校验备份文件是否存在且 custom-format 文件可被 pg_restore 读取。
# 用法：./scripts/verify-postgres-backup.sh /path/to/backup-directory

if [[ $# -ne 1 ]]; then
  echo "用法：$0 <backup-directory>" >&2
  exit 64
fi

backup_dir="$1"
database_backup="${backup_dir}/database.dump"
globals_backup="${backup_dir}/globals.sql"

if [[ ! -f "${database_backup}" || ! -s "${database_backup}" ]]; then
  echo "数据库备份不存在或为空：${database_backup}" >&2
  exit 1
fi

if [[ ! -f "${globals_backup}" || ! -s "${globals_backup}" ]]; then
  echo "全局对象备份不存在或为空：${globals_backup}" >&2
  exit 1
fi

pg_restore --list "${database_backup}" >/dev/null

echo "备份文件校验通过：${backup_dir}"

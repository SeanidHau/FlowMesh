#!/usr/bin/env bash

# 作用：为 FlowMesh 材料桶启用版本化并配置逻辑删除对象的生命周期清理。
# 脚本只操作显式传入的对象存储桶；执行前必须确认该桶专用于 FlowMesh 材料，避免误清理其他业务对象。

set -Eeuo pipefail

bucket="${FLOWMESH_OBJECT_STORAGE_BUCKET:-}"
retention_days="${FLOWMESH_OBJECT_STORAGE_NONCURRENT_RETENTION_DAYS:-7}"
endpoint="${FLOWMESH_OBJECT_STORAGE_ENDPOINT:-}"

if [[ -z "${bucket}" ]]; then
  echo 'FLOWMESH_OBJECT_STORAGE_BUCKET 必须设置为专用材料桶。' >&2
  exit 64
fi
if [[ ! "${bucket}" =~ ^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$ ]]; then
  echo 'FLOWMESH_OBJECT_STORAGE_BUCKET 不是合法的 S3 bucket 名称。' >&2
  exit 64
fi
if [[ ! "${retention_days}" =~ ^[1-9][0-9]*$ ]]; then
  echo 'FLOWMESH_OBJECT_STORAGE_NONCURRENT_RETENTION_DAYS 必须是正整数。' >&2
  exit 64
fi
if ! command -v aws >/dev/null 2>&1; then
  echo '当前环境未安装 aws CLI。' >&2
  exit 127
fi

aws_args=()
if [[ -n "${endpoint}" ]]; then
  case "${endpoint}" in
    https://*) aws_args+=(--endpoint-url "${endpoint}") ;;
    *)
      echo 'FLOWMESH_OBJECT_STORAGE_ENDPOINT 必须使用 HTTPS。' >&2
      exit 64
      ;;
  esac
fi
if [[ -n "${AWS_REGION:-}" ]]; then
  aws_args+=(--region "${AWS_REGION}")
fi

lifecycle_file="$(mktemp "${TMPDIR:-/tmp}/flowmesh-object-lifecycle.XXXXXX.json")"
cleanup() {
  rm -f -- "${lifecycle_file}"
}
trap cleanup EXIT
umask 077

cat > "${lifecycle_file}" <<JSON
{
  "Rules": [
    {
      "ID": "flowmesh-materials-noncurrent-retention",
      "Status": "Enabled",
      "Filter": {"Prefix": ""},
      "Expiration": {"ExpiredObjectDeleteMarker": true},
      "NoncurrentVersionExpiration": {"NoncurrentDays": ${retention_days}},
      "AbortIncompleteMultipartUpload": {"DaysAfterInitiation": 1}
    }
  ]
}
JSON

# 作用：开启版本化，让应用的 RemoveObject 先生成删除标记，再由生命周期策略清理非当前版本。
aws "${aws_args[@]}" s3api put-bucket-versioning \
  --bucket "${bucket}" \
  --versioning-configuration Status=Enabled

# 作用：提交材料桶生命周期策略；当前版本不会因策略自动删除，避免误删仍在使用的材料。
aws "${aws_args[@]}" s3api put-bucket-lifecycle-configuration \
  --bucket "${bucket}" \
  --lifecycle-configuration "file://${lifecycle_file}"

echo "对象存储生命周期配置完成：bucket=${bucket}，非当前版本保留 ${retention_days} 天。"

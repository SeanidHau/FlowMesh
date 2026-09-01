#!/usr/bin/env bash
# 作用：在启动 Compose 前拒绝公开占位凭据和无效 JWT 密钥。

set -euo pipefail

env_file="${1:-.env}"
if [[ ! -f "$env_file" ]]; then
  echo "环境变量文件不存在：$env_file" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "$env_file"
set +a

required_vars=(POSTGRES_PASSWORD IAM_DB_PASSWORD SUPPLIER_DB_PASSWORD WORKFLOW_DB_PASSWORD REDIS_PASSWORD JWT_SIGNING_KEY)
for variable in "${required_vars[@]}"; do
  value="${!variable:-}"
  if [[ -z "$value" || "$value" == change-me* || "$value" == replace-with-* ]]; then
    echo "$variable 必须替换为真实的本地或部署凭据。" >&2
    exit 1
  fi
done

decoded_bytes="$(printf '%s' "$JWT_SIGNING_KEY" | openssl base64 -d -A 2>/dev/null | wc -c | tr -d ' ')"
if [[ "$decoded_bytes" -lt 32 ]]; then
  echo "JWT_SIGNING_KEY 必须是至少 32 字节随机数据的 Base64 编码值。" >&2
  exit 1
fi

echo "Compose 环境变量校验通过。"

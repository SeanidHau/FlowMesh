#!/usr/bin/env bash
# 作用：静态检查生产 Helm 覆盖值是否仍指向单机演示依赖，避免误把本地拓扑发布到生产。

set -euo pipefail

values_file="${1:-infra/helm/flowmesh/values-production.yaml}"
if [[ ! -f "${values_file}" ]]; then
  echo "生产 values 文件不存在：${values_file}" >&2
  exit 2
fi

require_value() {
  local pattern="$1"
  local message="$2"
  if ! grep -Eq "${pattern}" "${values_file}"; then
    echo "生产配置缺少：${message}" >&2
    exit 1
  fi
}

require_value '^global:[[:space:]]*$' 'global 配置块'
require_value '^  production:[[:space:]]*true[[:space:]]*$' '生产模式必须启用'
require_value '^  imageRegistry:[[:space:]]*[^[:space:]]+' '生产镜像仓库地址'
require_value '^  existingSecret:[[:space:]]*[^[:space:]]+' '外部 Secret 引用'
require_value '^networkPolicy:[[:space:]]*$' 'NetworkPolicy 配置块'
require_value '^  enabled:[[:space:]]*true[[:space:]]*$' 'NetworkPolicy 必须启用'
require_value '^objectStorage:[[:space:]]*$' '对象存储配置块'
require_value '^  endpoint:[[:space:]]*https://' '生产对象存储必须使用 HTTPS'
require_value '^fileScan:[[:space:]]*$' '文件扫描配置块'
require_value '^  enabled:[[:space:]]*true[[:space:]]*$' '生产文件扫描必须启用'
require_value '^    replicas:[[:space:]]*2[[:space:]]*$' 'IAM 双副本'
require_value '^  supplier:[[:space:]]*$' 'supplier 服务配置块'
require_value '^  workflow:[[:space:]]*$' 'workflow 服务配置块'
require_value '^  risk:[[:space:]]*$' 'risk 服务配置块'
require_value '^  notificationAudit:[[:space:]]*$' 'notification-audit 服务配置块'

if grep -Eq 'host:[[:space:]]*(postgres|redis)$|namesrvAddr:[[:space:]]*rocketmq-namesrv' "${values_file}"; then
  echo '生产 values 仍使用本地依赖服务名（postgres/redis/rocketmq-namesrv）。' >&2
  exit 1
fi

echo "生产 Helm 配置静态检查通过：${values_file}"

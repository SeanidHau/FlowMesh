#!/usr/bin/env bash
# 作用：验证强依赖基础设施已纳入对应服务的 Kubernetes readiness 检查。
# 该契约只读取配置，不启动服务或连接外部基础设施。

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
gateway_config="${repo_root}/services/gateway-service/src/main/resources/application.yml"
iam_config="${repo_root}/services/iam-service/src/main/resources/application.yml"

for file in "${gateway_config}" "${iam_config}"; do
  [[ -s "${file}" ]] || {
    printf 'readiness 配置文件不存在或为空：%s\n' "${file}" >&2
    exit 1
  }
done

awk '
  /^        readiness:$/ { in_readiness = 1; next }
  in_readiness && /^          include:/ {
    if ($0 !~ /readinessState/ || $0 !~ /redis/) {
      exit 1
    }
    found = 1
    in_readiness = 0
  }
  in_readiness && /^[^ ]/ { in_readiness = 0 }
  END { exit found ? 0 : 1 }
' "${gateway_config}" || {
  echo 'Gateway readiness 必须同时包含 readinessState 和 redis。' >&2
  exit 1
}

awk '
  /^        readiness:$/ { in_readiness = 1; next }
  in_readiness && /^          include:/ {
    if ($0 !~ /readinessState/ || $0 !~ /db/ || $0 !~ /redis/) {
      exit 1
    }
    found = 1
    in_readiness = 0
  }
  in_readiness && /^[^ ]/ { in_readiness = 0 }
  END { exit found ? 0 : 1 }
' "${iam_config}" || {
  echo 'IAM readiness 必须同时包含 readinessState、db 和 redis。' >&2
  exit 1
}

grep -F -- 'FLOWMESH_LOGIN_RATE_LIMIT_ENABLED' "${iam_config}" >/dev/null
grep -F -- '  health:' "${iam_config}" >/dev/null
grep -F -- '    redis:' "${iam_config}" >/dev/null

echo 'Readiness dependency contract passed.'

#!/usr/bin/env bash
# 作用：离线验证五个业务服务使用一致的 Flyway 生产安全策略。

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
services=(iam supplier workflow risk notification-audit)

for service in "${services[@]}"; do
  file="${repo_root}/services/${service}-service/src/main/resources/application.yml"
  test -s "${file}"
  for marker in \
    'validate-on-migrate: true' \
    'clean-disabled: true' \
    'out-of-order: false' \
    'baseline-on-migrate: false'; do
    grep -F -- "${marker}" "${file}" >/dev/null || {
      echo "${file} 缺少 Flyway 生产安全配置：${marker}" >&2
      exit 1
    }
  done
done

echo 'Flyway production contract passed.'

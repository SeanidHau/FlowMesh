#!/usr/bin/env bash
# 作用：离线验证源码安全工作流覆盖 CodeQL、依赖审查和最小权限配置。

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
workflow="${repo_root}/.github/workflows/security.yml"

test -s "${workflow}"

grep -F -- 'github/codeql-action/init@v4' "${workflow}" >/dev/null
grep -F -- 'github/codeql-action/analyze@v4' "${workflow}" >/dev/null
grep -F -- 'actions/dependency-review-action@v4' "${workflow}" >/dev/null
grep -F -- 'java-kotlin' "${workflow}" >/dev/null
grep -F -- 'javascript-typescript' "${workflow}" >/dev/null
grep -F -- 'build-mode: manual' "${workflow}" >/dev/null
grep -F -- 'build-mode: none' "${workflow}" >/dev/null
grep -F -- 'fail-on-severity: high' "${workflow}" >/dev/null
grep -F -- 'security-events: write' "${workflow}" >/dev/null

if grep -E '^[[:space:]]*(JWT_SIGNING_KEY|REDIS_PASSWORD|DB_PASSWORD|SECRET_KEY):' "${workflow}" >/dev/null; then
  echo '源码安全工作流不得注入运行时凭据。' >&2
  exit 1
fi

echo 'Security workflow contract passed.'

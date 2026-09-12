#!/usr/bin/env bash
# 作用：离线验证 IAM 安全审计表具备数据库层 append-only 保护。

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
migration="${repo_root}/services/iam-service/src/main/resources/db/migration/V7__protect_iam_audit_events.sql"

test -s "${migration}"
for marker in \
  'prevent_iam_audit_event_mutation' \
  'BEFORE UPDATE OR DELETE ON iam_audit_events' \
  'BEFORE TRUNCATE ON iam_audit_events' \
  'REVOKE UPDATE, DELETE, TRUNCATE ON iam_audit_events FROM PUBLIC'; do
  grep -F -- "${marker}" "${migration}" >/dev/null || {
    echo "IAM 审计 append-only 迁移缺少保护：${marker}" >&2
    exit 1
  }
done

echo 'IAM audit append-only contract passed.'

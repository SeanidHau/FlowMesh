#!/usr/bin/env bash
# 作用：离线验证 IAM 租户数据的 RLS 迁移、认证上下文和逐租户清理边界。

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
migration="${repo_root}/services/iam-service/src/main/resources/db/migration/V9__enforce_iam_tenant_isolation.sql"
auth_service="${repo_root}/services/iam-service/src/main/java/com/flowmesh/iam/application/auth/AuthApplicationService.java"
cleanup_job="${repo_root}/services/iam-service/src/main/java/com/flowmesh/iam/application/auth/RefreshTokenCleanupJob.java"
cleanup_service="${repo_root}/services/iam-service/src/main/java/com/flowmesh/iam/application/auth/RefreshTokenTenantCleanupService.java"
refresh_request="${repo_root}/services/iam-service/src/main/java/com/flowmesh/iam/api/dto/RefreshRequest.java"
logout_request="${repo_root}/services/iam-service/src/main/java/com/flowmesh/iam/api/dto/LogoutRequest.java"
refresh_mapper="${repo_root}/services/iam-service/src/main/resources/mapper/RefreshTokenRepository.xml"

test -s "${migration}"
for table in iam_users iam_user_roles iam_refresh_tokens iam_audit_events; do
  grep -F -- "ALTER TABLE ${table} ENABLE ROW LEVEL SECURITY" "${migration}" >/dev/null || {
    echo "IAM RLS 迁移缺少启用策略：${table}" >&2
    exit 1
  }
  grep -F -- "ALTER TABLE ${table} FORCE ROW LEVEL SECURITY" "${migration}" >/dev/null || {
    echo "IAM RLS 迁移缺少强制策略：${table}" >&2
    exit 1
  }
done

grep -F -- 'ADD COLUMN tenant_id VARCHAR(64)' "${migration}" >/dev/null
grep -F -- 'fk_iam_refresh_tokens_user_tenant' "${migration}" >/dev/null
grep -F -- 'fk_iam_audit_events_tenant' "${migration}" >/dev/null
grep -F -- "current_setting('app.tenant_id', true)" "${migration}" >/dev/null
grep -F -- 'tenant_id' "${refresh_mapper}" >/dev/null
grep -F -- 'tenantRlsInitializer.initialize(tenantId);' "${auth_service}" >/dev/null
grep -F -- 'tenantRepository.findAllIds()' "${cleanup_job}" >/dev/null
grep -F -- '@Transactional(propagation = Propagation.REQUIRES_NEW)' "${cleanup_service}" >/dev/null
grep -F -- 'tenantRlsInitializer.initialize(tenantId);' "${cleanup_service}" >/dev/null
grep -F -- 'String tenantId' "${refresh_request}" >/dev/null
grep -F -- 'String tenantId' "${logout_request}" >/dev/null

echo 'IAM RLS contract passed.'

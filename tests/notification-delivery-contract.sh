#!/usr/bin/env bash
# 作用：离线验证外部通知可靠投递的安全边界、重试状态机和部署开关。
set -Eeuo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
migration="${repo_root}/services/notification-audit-service/src/main/resources/db/migration/V3__create_notification_delivery_outbox.sql"
renewal_migration="${repo_root}/services/notification-audit-service/src/main/resources/db/migration/V6__renew_notification_delivery_claim.sql"
publisher="${repo_root}/services/notification-audit-service/src/main/java/com/flowmesh/notificationaudit/messaging/NotificationDeliveryPublisher.java"
claim_service="${repo_root}/services/notification-audit-service/src/main/java/com/flowmesh/notificationaudit/messaging/NotificationDeliveryClaimService.java"
webhook="${repo_root}/services/notification-audit-service/src/main/java/com/flowmesh/notificationaudit/messaging/NotificationWebhookClient.java"
template="${repo_root}/infra/helm/flowmesh/templates/notification-audit.yaml"
production_values="${repo_root}/infra/helm/flowmesh/values-production.yaml"

grep -F 'FORCE ROW LEVEL SECURITY' "${migration}" >/dev/null
grep -F 'flowmesh_audit_delivery' "${migration}" >/dev/null
grep -F 'SECURITY DEFINER' "${migration}" >/dev/null
grep -F 'FOR UPDATE SKIP LOCKED' "${migration}" >/dev/null
grep -F 'claim_token' "${migration}" >/dev/null
grep -F 'renew_notification_delivery' "${renewal_migration}" >/dev/null
grep -F 'SECURITY DEFINER' "${renewal_migration}" >/dev/null
grep -F 'claimed_until > now()' "${renewal_migration}" >/dev/null
grep -F 'GRANT USAGE, CREATE ON SCHEMA audit TO flowmesh_audit_delivery' "${renewal_migration}" >/dev/null
grep -F 'REVOKE CREATE ON SCHEMA audit FROM flowmesh_audit_delivery' "${renewal_migration}" >/dev/null
grep -F 'renew(' "${claim_service}" >/dev/null
grep -F 'claimService.renew(delivery)' "${publisher}" >/dev/null
grep -F 'REVOKE ALL ON notification_deliveries FROM flowmesh_audit' "${migration}" >/dev/null
grep -F 'SET ROLE flowmesh_audit_delivery' "${repo_root}/services/notification-audit-service/src/main/resources/db/migration/V4__grant_notification_delivery_retention.sql" >/dev/null
grep -F 'getMaxAttempts' "${publisher}" >/dev/null
grep -F 'DEAD_LETTER' "${publisher}" >/dev/null
grep -F 'flowmesh.notification.delivery.confirmation_failed' "${publisher}" >/dev/null
grep -F 'X-FlowMesh-Signature' "${webhook}" >/dev/null
grep -F 'Idempotency-Key' "${webhook}" >/dev/null
grep -F '"https".equalsIgnoreCase' "${repo_root}/services/notification-audit-service/src/main/java/com/flowmesh/notificationaudit/config/NotificationDeliveryProperties.java" >/dev/null
grep -F 'new URI' "${repo_root}/services/notification-audit-service/src/main/java/com/flowmesh/notificationaudit/config/NotificationDeliveryProperties.java" >/dev/null
grep -F 'uri.getUserInfo() == null' "${repo_root}/services/notification-audit-service/src/main/java/com/flowmesh/notificationaudit/config/NotificationDeliveryProperties.java" >/dev/null
grep -F 'sendTimeoutMillis > 60_000' "${repo_root}/services/notification-audit-service/src/main/java/com/flowmesh/notificationaudit/config/NotificationDeliveryProperties.java" >/dev/null
grep -F 'FLOWMESH_NOTIFICATION_SIGNING_SECRET' "${template}" >/dev/null
grep -A4 -F 'notificationDelivery:' "${production_values}" | grep -F 'enabled: true' >/dev/null
grep -A4 -F 'notificationDelivery:' "${production_values}" | grep -F 'webhookUrl: https://' >/dev/null

if grep -Eq 'log\.(info|warn|error).*delivery.*content|System\.out.*content' "${publisher}" "${webhook}"; then
  echo '外部通知投递日志不得包含通知内容。' >&2
  exit 1
fi

echo 'Notification delivery contract passed.'

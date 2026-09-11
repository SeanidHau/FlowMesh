#!/usr/bin/env bash
# 作用：离线验证外部通知可靠投递的安全边界、重试状态机和部署开关。
set -Eeuo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
migration="${repo_root}/services/notification-audit-service/src/main/resources/db/migration/V3__create_notification_delivery_outbox.sql"
publisher="${repo_root}/services/notification-audit-service/src/main/java/com/flowmesh/notificationaudit/messaging/NotificationDeliveryPublisher.java"
webhook="${repo_root}/services/notification-audit-service/src/main/java/com/flowmesh/notificationaudit/messaging/NotificationWebhookClient.java"
template="${repo_root}/infra/helm/flowmesh/templates/notification-audit.yaml"
production_values="${repo_root}/infra/helm/flowmesh/values-production.yaml"

grep -F 'FORCE ROW LEVEL SECURITY' "${migration}" >/dev/null
grep -F 'flowmesh_audit_delivery' "${migration}" >/dev/null
grep -F 'SECURITY DEFINER' "${migration}" >/dev/null
grep -F 'FOR UPDATE SKIP LOCKED' "${migration}" >/dev/null
grep -F 'claim_token' "${migration}" >/dev/null
grep -F 'REVOKE ALL ON notification_deliveries FROM flowmesh_audit' "${migration}" >/dev/null
grep -F 'getMaxAttempts' "${publisher}" >/dev/null
grep -F 'DEAD_LETTER' "${publisher}" >/dev/null
grep -F 'X-FlowMesh-Signature' "${webhook}" >/dev/null
grep -F 'Idempotency-Key' "${webhook}" >/dev/null
grep -F 'https://' "${repo_root}/services/notification-audit-service/src/main/java/com/flowmesh/notificationaudit/config/NotificationDeliveryProperties.java" >/dev/null
grep -F 'FLOWMESH_NOTIFICATION_SIGNING_SECRET' "${template}" >/dev/null
grep -A4 -F 'notificationDelivery:' "${production_values}" | grep -F 'enabled: true' >/dev/null
grep -A4 -F 'notificationDelivery:' "${production_values}" | grep -F 'webhookUrl: https://' >/dev/null

if grep -Eq 'log\.(info|warn|error).*delivery.*content|System\.out.*content' "${publisher}" "${webhook}"; then
  echo '外部通知投递日志不得包含通知内容。' >&2
  exit 1
fi

echo 'Notification delivery contract passed.'

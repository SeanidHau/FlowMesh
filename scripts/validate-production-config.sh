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
require_value '^  topologySpread:[[:space:]]*$' '生产拓扑分散配置块'
require_value '^    whenUnsatisfiable:[[:space:]]*DoNotSchedule[[:space:]]*$' '生产副本必须跨节点分布'
require_value '^  imageRegistry:[[:space:]]*[^[:space:]]+' '生产镜像仓库地址'
require_value '^  existingSecret:[[:space:]]*[^[:space:]]+' '外部 Secret 引用'
require_value '^postgresql:[[:space:]]*$' 'PostgreSQL 配置块'
require_value '^  sslMode:[[:space:]]*[^[:space:]]+' 'PostgreSQL TLS 模式'
require_value '^migrations:[[:space:]]*$' 'Flyway 迁移 Job 配置块'
require_value '^  backoffLimit:[[:space:]]*[1-9][0-9]*[[:space:]]*$' 'Flyway 迁移 Job 重试上限'
require_value '^  activeDeadlineSeconds:[[:space:]]*[1-9][0-9]*[[:space:]]*$' 'Flyway 迁移 Job 截止时间'
require_value '^redis:[[:space:]]*$' 'Redis 配置块'
require_value '^  sslEnabled:[[:space:]]*true[[:space:]]*$' 'Redis TLS 必须启用'
require_value '^networkPolicy:[[:space:]]*$' 'NetworkPolicy 配置块'
require_value '^  enabled:[[:space:]]*true[[:space:]]*$' 'NetworkPolicy 必须启用'
require_value '^  ingressNamespace:[[:space:]]*[^[:space:]]+' 'Gateway 入站 Ingress Controller 命名空间'
require_value '^  egress:[[:space:]]*$' 'NetworkPolicy 出站配置块'
require_value '^    enabled:[[:space:]]*true[[:space:]]*$' '生产 NetworkPolicy 出站必须启用'
require_value '^ingress:[[:space:]]*$' 'Ingress 配置块'
require_value '^  enabled:[[:space:]]*true[[:space:]]*$' '生产 Ingress 必须启用'
require_value '^objectStorage:[[:space:]]*$' '对象存储配置块'
require_value '^  endpoint:[[:space:]]*https://' '生产对象存储必须使用 HTTPS'
require_value '^rocketmq:[[:space:]]*$' 'RocketMQ 配置块'
require_value '^  accessChannel:[[:space:]]*[^[:space:]]+' 'RocketMQ 访问通道'
require_value '^fileScan:[[:space:]]*$' '文件扫描配置块'
require_value '^  enabled:[[:space:]]*true[[:space:]]*$' '生产文件扫描必须启用'
require_value '^gateway:[[:space:]]*$' 'Gateway 配置块'
require_value '^  rateLimit:[[:space:]]*$' 'Gateway Redis 限流配置块'
require_value '^    replenishRate:[[:space:]]*[1-9][0-9]*[[:space:]]*$' 'Gateway 限流补充速率'
require_value '^    burstCapacity:[[:space:]]*[1-9][0-9]*[[:space:]]*$' 'Gateway 限流桶容量'
require_value '^    requestedTokens:[[:space:]]*[1-9][0-9]*[[:space:]]*$' 'Gateway 单请求令牌数'
require_value '^    clientIpHeader:[[:space:]]*[^[:space:]]+' 'Gateway 可信客户端地址请求头'
require_value '^backup:[[:space:]]*$' 'PostgreSQL 备份配置块'
require_value '^  enabled:[[:space:]]*true[[:space:]]*$' '生产 PostgreSQL 备份必须启用'
require_value '^  credentialsSecret:[[:space:]]*[^[:space:]]+' '备份凭据 Secret'
require_value '^    user:[[:space:]]*[A-Za-z_][A-Za-z0-9_]*[[:space:]]*$' '备份专用数据库账号'
require_value '^retention:[[:space:]]*$' '数据生命周期清理配置块'
require_value '^  enabled:[[:space:]]*true[[:space:]]*$' '生产数据生命周期清理必须启用'
require_value '^  credentialsSecret:[[:space:]]*[^[:space:]]+' '生命周期维护凭据 Secret'
require_value '^observability:[[:space:]]*$' '生产观测配置块'
require_value '^  serviceMonitor:[[:space:]]*$' '生产 ServiceMonitor 配置块'
require_value '^    enabled:[[:space:]]*true[[:space:]]*$' '生产 ServiceMonitor 必须启用'
require_value '^  prometheusRule:[[:space:]]*$' '生产 PrometheusRule 配置块'
require_value '^    enabled:[[:space:]]*true[[:space:]]*$' '生产 PrometheusRule 必须启用'
require_value '^    replicas:[[:space:]]*2[[:space:]]*$' 'IAM 双副本'
require_value '^  supplier:[[:space:]]*$' 'supplier 服务配置块'
require_value '^  workflow:[[:space:]]*$' 'workflow 服务配置块'
require_value '^  risk:[[:space:]]*$' 'risk 服务配置块'
require_value '^  notificationAudit:[[:space:]]*$' 'notification-audit 服务配置块'
require_value '^    notificationDelivery:[[:space:]]*$' '外部通知投递配置块'
require_value '^      enabled:[[:space:]]*true[[:space:]]*$' '生产外部通知投递必须启用'
require_value '^      webhookUrl:[[:space:]]*https://' '生产外部通知 Webhook 必须使用 HTTPS'
require_value '^      signingSecretKey:[[:space:]]*[^[:space:]]+' '外部通知签名 Secret 键名'

ruby - "${values_file}" <<'RUBY'
require "yaml"

values = YAML.load_file(ARGV.fetch(0))
topology_spread = values.fetch("global").fetch("topologySpread")
raise "生产副本拓扑分散必须使用 DoNotSchedule" unless topology_spread.fetch("whenUnsatisfiable") == "DoNotSchedule"
observability = values.fetch("observability")
raise "生产 ServiceMonitor 必须启用" unless observability.dig("serviceMonitor", "enabled") == true
raise "生产 PrometheusRule 必须启用" unless observability.dig("prometheusRule", "enabled") == true
backup = values.fetch("backup")
raise "生产 PostgreSQL 备份必须启用" unless backup.fetch("enabled") == true
raise "生产备份必须配置凭据 Secret" if backup.fetch("credentialsSecret", "").to_s.empty?
rocketmq = values.fetch("rocketmq")
raise "生产 RocketMQ Producer 必须启用 TLS" unless rocketmq.dig("producer", "tlsEnabled") == true
raise "生产 RocketMQ Consumer 必须启用 TLS" unless rocketmq.dig("consumer", "tlsEnabled") == true
postgresql = values.fetch("postgresql")
raise "生产 PostgreSQL 不得使用明文连接" if postgresql.fetch("sslMode") == "disable"
raise "生产 PostgreSQL 必须配置 sslMode" if postgresql.fetch("sslMode", "").to_s.empty?
raise "生产 PostgreSQL 默认必须使用 verify-full" unless postgresql.fetch("sslMode") == "verify-full"
raise "生产 PostgreSQL 必须配置 CA Secret" if postgresql.fetch("caSecretName", "").to_s.empty?
raise "生产 Redis 必须启用 TLS" unless values.fetch("redis").fetch("sslEnabled") == true
backup_postgresql = backup.fetch("postgres")
backup_user = backup_postgresql.fetch("user", "").to_s
raise "生产 PostgreSQL 备份必须使用专用数据库账号" if backup_user.empty? || %w[postgres flowmesh].include?(backup_user)
raise "生产 PostgreSQL 备份不得使用明文连接" if backup_postgresql.fetch("sslMode") == "disable"
raise "生产 PostgreSQL 备份必须配置 sslMode" if backup_postgresql.fetch("sslMode", "").to_s.empty?
raise "生产 PostgreSQL 备份默认必须使用 verify-full" unless backup_postgresql.fetch("sslMode") == "verify-full"
raise "生产 PostgreSQL 备份必须配置 CA Secret" if backup_postgresql.fetch("caSecretName", "").to_s.empty?
raise "生产 PostgreSQL 备份必须启用远端对象校验" unless backup.dig("s3", "verifyRemote") == true
retention = values.fetch("retention")
raise "生产数据生命周期清理必须启用" unless retention.fetch("enabled") == true
raise "生产生命周期清理必须配置凭据 Secret" if retention.fetch("credentialsSecret", "").to_s.empty?
raise "生产生命周期清理必须使用 YES 确认值" unless retention.fetch("confirmation") == "YES"
raise "生产生命周期清理不得使用明文连接" if retention.dig("postgres", "sslMode") == "disable"
raise "生产生命周期清理必须配置 sslMode" if retention.dig("postgres", "sslMode").to_s.empty?
raise "生产生命周期清理默认必须使用 verify-full" unless retention.dig("postgres", "sslMode") == "verify-full"
raise "生产生命周期清理必须配置 CA Secret" if retention.dig("postgres", "caSecretName").to_s.empty?
notification_delivery = values.dig("services", "notificationAudit", "notificationDelivery") || {}
raise "生产外部通知投递必须启用" unless notification_delivery.fetch("enabled") == true
raise "生产外部通知 Webhook 必须使用 HTTPS" unless notification_delivery.fetch("webhookUrl", "").to_s.start_with?("https://")
raise "生产外部通知必须配置签名 Secret 键名" if notification_delivery.fetch("signingSecretKey", "").to_s.empty?
%w[outboxRetentionDays dlqRetentionDays inboxRetentionDays batchSize].each do |field|
  raise "生产生命周期清理 #{field} 必须是正整数" unless retention.fetch(field).to_i.positive?
end
puts "生产备份配置结构校验通过。"
RUBY

if grep -Eq 'host:[[:space:]]*(postgres|redis)$|namesrvAddr:[[:space:]]*rocketmq-namesrv(:|$)' "${values_file}"; then
  echo '生产 values 仍使用本地依赖服务名（postgres/redis/rocketmq-namesrv）。' >&2
  exit 1
fi

echo "生产 Helm 配置静态检查通过：${values_file}"

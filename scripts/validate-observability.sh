#!/usr/bin/env bash
# 作用：校验仓库内 Prometheus 规则、抓取配置和 Grafana Dashboard 的基本结构。

set -euo pipefail

rules_file="infra/compose/prometheus-rules.yml"
scrape_file="infra/compose/prometheus.yml"
alertmanager_file="infra/compose/alertmanager.yml"
dashboard_file="infra/compose/grafana/dashboards/flowmesh-overview.json"

ruby - "${rules_file}" "${scrape_file}" "${alertmanager_file}" <<'RUBY'
require "yaml"

rules = YAML.load_file(ARGV.fetch(0))
scrape = YAML.load_file(ARGV.fetch(1))
alertmanager = YAML.load_file(ARGV.fetch(2))

groups = rules.fetch("groups")
abort "Prometheus 规则必须包含 groups" unless groups.is_a?(Array) && !groups.empty?
alerts = []
groups.each do |group|
  group.fetch("rules").each do |rule|
    %w[alert expr labels annotations].each { |key| rule.fetch(key) }
    alerts << rule.fetch("alert")
  end
end
required_alerts = %w[
  FlowMeshServiceDown
  FlowMeshOutboxBacklog
  FlowMeshDeadLetterEvents
  FlowMeshConsumerFailures
  FlowMeshConsumerProcessingLatency
  FlowMeshOutboxConfirmationFailures
  FlowMeshHttp5xxRate
  FlowMeshGatewayRateLimitRedisErrors
  FlowMeshGatewayRateLimitDenied
]
missing_alerts = required_alerts - alerts
abort "Prometheus 告警缺少：#{missing_alerts.join(', ')}" unless missing_alerts.empty?

jobs = scrape.fetch("scrape_configs")
abort "Prometheus 抓取配置不能为空" unless jobs.is_a?(Array) && !jobs.empty?
jobs.each { |job| job.fetch("job_name"); job.fetch("static_configs") }

alertmanagers = scrape.fetch("alerting").fetch("alertmanagers")
abort "Prometheus 必须配置 Alertmanager" unless alertmanagers.any? { |item| item.fetch("static_configs").any? }
route = alertmanager.fetch("route")
receivers = alertmanager.fetch("receivers").map { |receiver| receiver.fetch("name") }
abort "Alertmanager 默认 receiver 未定义" unless receivers.include?(route.fetch("receiver"))
puts "Prometheus 配置结构校验通过。"
RUBY

jq empty "${dashboard_file}"
echo "Grafana Dashboard JSON 校验通过。"

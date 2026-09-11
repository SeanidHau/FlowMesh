#!/usr/bin/env bash
# 作用：校验仓库内 Prometheus 规则、抓取配置和 Grafana Dashboard 的基本结构。

set -euo pipefail

rules_file="infra/compose/prometheus-rules.yml"
scrape_file="infra/compose/prometheus.yml"
dashboard_file="infra/compose/grafana/dashboards/flowmesh-overview.json"

ruby - "${rules_file}" "${scrape_file}" <<'RUBY'
require "yaml"

rules = YAML.load_file(ARGV.fetch(0))
scrape = YAML.load_file(ARGV.fetch(1))

groups = rules.fetch("groups")
abort "Prometheus 规则必须包含 groups" unless groups.is_a?(Array) && !groups.empty?
groups.each do |group|
  group.fetch("rules").each do |rule|
    %w[alert expr labels annotations].each { |key| rule.fetch(key) }
  end
end

jobs = scrape.fetch("scrape_configs")
abort "Prometheus 抓取配置不能为空" unless jobs.is_a?(Array) && !jobs.empty?
jobs.each { |job| job.fetch("job_name"); job.fetch("static_configs") }
puts "Prometheus 配置结构校验通过。"
RUBY

jq empty "${dashboard_file}"
echo "Grafana Dashboard JSON 校验通过。"

#!/usr/bin/env bash
# 作用：只读验证目标环境的 Prometheus、Alertmanager 已就绪，并能看到 FlowMesh 服务和告警规则。
# 脚本不会创建、修改或删除任何观测资源；生产环境应通过内网地址或 HTTPS 访问这些端点。

set -Eeuo pipefail

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    printf '运行时观测预检缺少命令：%s\n' "$1" >&2
    exit 127
  }
}

require_value() {
  local name="$1"
  local value="$2"
  [[ -n "${value}" ]] || {
    printf '运行时观测预检缺少环境变量：%s\n' "${name}" >&2
    exit 2
  }
}

require_command curl
require_command ruby

prometheus_url="${FLOWMESH_PROMETHEUS_URL:-}"
alertmanager_url="${FLOWMESH_ALERTMANAGER_URL:-}"
timeout_seconds="${FLOWMESH_OBSERVABILITY_TIMEOUT_SECONDS:-5}"
require_value FLOWMESH_PROMETHEUS_URL "${prometheus_url}"
require_value FLOWMESH_ALERTMANAGER_URL "${alertmanager_url}"
[[ "${timeout_seconds}" =~ ^[0-9]+$ && "${timeout_seconds}" -gt 0 ]] || {
  echo 'FLOWMESH_OBSERVABILITY_TIMEOUT_SECONDS 必须是正整数。' >&2
  exit 2
}

prometheus_url="${prometheus_url%/}"
alertmanager_url="${alertmanager_url%/}"

fetch_json() {
  local endpoint="$1"
  shift
  curl --fail --silent --show-error \
    --connect-timeout "${timeout_seconds}" --max-time "${timeout_seconds}" \
    --header 'Accept: application/json' "${endpoint}" "$@"
}

validate_json() {
  local name="$1"
  ruby -rjson -e '
    body = JSON.parse(STDIN.read)
    abort "#{ARGV.fetch(0)} 响应 status 不是 success" unless body.fetch("status") == "success"
  ' "${name}"
}

prometheus_ready="$(curl --fail --silent --show-error \
  --connect-timeout "${timeout_seconds}" --max-time "${timeout_seconds}" \
  "${prometheus_url}/-/ready")"
[[ "${prometheus_ready}" == *"Ready"* ]] || {
  echo 'Prometheus 未返回 Ready。' >&2
  exit 1
}

alertmanager_ready="$(curl --fail --silent --show-error \
  --connect-timeout "${timeout_seconds}" --max-time "${timeout_seconds}" \
  "${alertmanager_url}/-/ready")"
[[ "${alertmanager_ready}" == *"Ready"* ]] || {
  echo 'Alertmanager 未返回 Ready。' >&2
  exit 1
}

query='up{job=~"gateway|iam|supplier|workflow|risk|notification-audit"}'
query_response="$(fetch_json "${prometheus_url}/api/v1/query" --get --data-urlencode "query=${query}")"
printf '%s' "${query_response}" | validate_json 'Prometheus 服务可用性查询'

missing_jobs="$(printf '%s' "${query_response}" | ruby -rjson -e '
  body = JSON.parse(STDIN.read)
  expected = %w[gateway iam supplier workflow risk notification-audit]
  actual = body.fetch("data").fetch("result").map { |item| item.fetch("metric").fetch("job") }.uniq
  puts(expected - actual)
')"
[[ -z "${missing_jobs}" ]] || {
  echo "Prometheus 缺少 FlowMesh 服务目标：${missing_jobs}" >&2
  exit 1
}

rules_response="$(fetch_json "${prometheus_url}/api/v1/rules?type=alert")"
printf '%s' "${rules_response}" | validate_json 'Prometheus 告警规则查询'
missing_alerts="$(printf '%s' "${rules_response}" | ruby -rjson -e '
  body = JSON.parse(STDIN.read)
  expected = %w[FlowMeshServiceDown FlowMeshOutboxBacklog FlowMeshDeadLetterEvents FlowMeshConsumerFailures FlowMeshConsumerProcessingLatency FlowMeshOutboxConfirmationFailures]
  actual = body.fetch("data").fetch("groups").flat_map { |group| group.fetch("rules") }.map { |rule| rule.fetch("name") }
  puts(expected - actual)
')"
[[ -z "${missing_alerts}" ]] || {
  echo "Prometheus 缺少 FlowMesh 关键告警：${missing_alerts}" >&2
  exit 1
}

echo '运行时观测预检通过：Prometheus、Alertmanager、FlowMesh targets 和关键告警均可用。'

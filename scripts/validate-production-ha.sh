#!/usr/bin/env bash
# 作用：只读检查目标生产依赖暴露的 HA 拓扑证据，并生成不可覆盖的证据报告。
# 该脚本不执行切换、写入、迁移或删除；实际故障切换和恢复仍必须按平台剧本单独演练。

set -Eeuo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/postgres-tls.sh"

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    printf '生产 HA 预检缺少命令：%s\n' "$1" >&2
    exit 127
  }
}

require_value() {
  local name="$1"
  local value="$2"
  [[ -n "${value}" ]] || {
    printf '生产 HA 预检缺少环境变量：%s\n' "${name}" >&2
    exit 2
  }
}

run_with_timeout() {
  local seconds="$1"
  shift
  if command -v timeout >/dev/null 2>&1; then
    timeout "${seconds}" "$@"
  elif command -v gtimeout >/dev/null 2>&1; then
    gtimeout "${seconds}" "$@"
  elif command -v perl >/dev/null 2>&1; then
    perl -e 'alarm shift; exec @ARGV' "${seconds}" "$@"
  else
    echo '生产 HA 预检需要 timeout、gtimeout 或 perl 之一。' >&2
    exit 127
  fi
}

parse_endpoint() {
  local endpoint="$1"
  if [[ "${endpoint}" =~ ^([^:]+):([0-9]+)$ ]]; then
    PREFLIGHT_ENDPOINT_HOST="${BASH_REMATCH[1]}"
    PREFLIGHT_ENDPOINT_PORT="${BASH_REMATCH[2]}"
  else
    printf '无效的 host:port 地址：%s\n' "${endpoint}" >&2
    exit 2
  fi
}

report_path="${FLOWMESH_HA_REPORT:-}"
require_value FLOWMESH_HA_REPORT "${report_path}"
if [[ -e "${report_path}" ]]; then
  echo "拒绝覆盖已有生产 HA 报告：${report_path}" >&2
  exit 64
fi
mkdir -p "$(dirname "${report_path}")"

timeout_seconds="${FLOWMESH_HA_TIMEOUT_SECONDS:-5}"
pg_host="${FLOWMESH_PG_HOST:-}"
pg_port="${FLOWMESH_PG_PORT:-5432}"
pg_database="${FLOWMESH_PG_DATABASE:-flowmesh}"
pg_user="${FLOWMESH_PG_USER:-}"
pg_sslmode="${FLOWMESH_PG_SSLMODE:-verify-full}"
pg_sslrootcert="${FLOWMESH_PG_SSLROOTCERT:-}"
expected_pg_replicas="${FLOWMESH_HA_EXPECTED_PG_REPLICAS:-}"
redis_hosts="${FLOWMESH_HA_REDIS_HOSTS:-}"
redis_password="${FLOWMESH_REDIS_PASSWORD:-}"
expected_redis_replicas="${FLOWMESH_HA_EXPECTED_REDIS_REPLICAS:-}"
namesrv_addresses="${FLOWMESH_ROCKETMQ_NAMESRV_ADDR:-}"
rocketmq_ca_file="${FLOWMESH_ROCKETMQ_CA_FILE:-}"
object_storage_endpoint="${FLOWMESH_OBJECT_STORAGE_ENDPOINT:-}"

require_value FLOWMESH_PG_HOST "${pg_host}"
require_value FLOWMESH_PG_USER "${pg_user}"
require_value FLOWMESH_HA_EXPECTED_PG_REPLICAS "${expected_pg_replicas}"
require_value FLOWMESH_HA_REDIS_HOSTS "${redis_hosts}"
require_value FLOWMESH_REDIS_PASSWORD "${redis_password}"
require_value FLOWMESH_HA_EXPECTED_REDIS_REPLICAS "${expected_redis_replicas}"
require_value FLOWMESH_ROCKETMQ_NAMESRV_ADDR "${namesrv_addresses}"
require_value FLOWMESH_OBJECT_STORAGE_ENDPOINT "${object_storage_endpoint}"

[[ "${timeout_seconds}" =~ ^[0-9]+$ && "${timeout_seconds}" -gt 0 ]] || {
  echo 'FLOWMESH_HA_TIMEOUT_SECONDS 必须是正整数。' >&2
  exit 2
}
[[ "${expected_pg_replicas}" =~ ^[1-9][0-9]*$ ]] || {
  echo 'FLOWMESH_HA_EXPECTED_PG_REPLICAS 必须是正整数。' >&2
  exit 2
}
[[ "${expected_redis_replicas}" =~ ^[1-9][0-9]*$ ]] || {
  echo 'FLOWMESH_HA_EXPECTED_REDIS_REPLICAS 必须是正整数。' >&2
  exit 2
}
[[ "${pg_sslmode}" =~ ^(verify-ca|verify-full)$ ]] || {
  echo '生产 HA 预检要求 PostgreSQL 使用 verify-ca 或 verify-full。' >&2
  exit 2
}
flowmesh_require_postgres_ca "${pg_sslmode}" "${pg_sslrootcert}"
[[ "${object_storage_endpoint}" == https://* ]] || {
  echo '生产 HA 预检要求对象存储使用 HTTPS。' >&2
  exit 2
}

require_command psql
require_command redis-cli
require_command openssl
require_command curl
require_command mktemp

IFS=',' read -r -a redis_endpoint_list <<< "${redis_hosts}"
redis_endpoints=()
for endpoint in "${redis_endpoint_list[@]}"; do
  endpoint="$(printf '%s' "${endpoint}" | tr -d '[:space:]')"
  [[ -n "${endpoint}" ]] || continue
  redis_endpoints+=("${endpoint}")
done
if [[ "${#redis_endpoints[@]}" -lt 2 ]]; then
  echo '生产 HA 预检至少需要两个 Redis host:port，使用逗号分隔。' >&2
  exit 2
fi

IFS=',' read -r -a nameserver_list <<< "${namesrv_addresses}"
nameservers=()
for nameserver in "${nameserver_list[@]}"; do
  nameserver="$(printf '%s' "${nameserver}" | tr -d '[:space:]')"
  [[ -n "${nameserver}" ]] || continue
  nameservers+=("${nameserver}")
done
if [[ "${#nameservers[@]}" -lt 2 ]]; then
  echo '生产 HA 预检至少需要两个 RocketMQ NameServer host:port，使用逗号分隔。' >&2
  exit 2
fi

temp_report="$(mktemp "${TMPDIR:-/tmp}/flowmesh-ha-report.XXXXXX")"
trap 'rm -f -- "${temp_report}"' EXIT
started_at="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"

export PGSSLMODE="${pg_sslmode}"
if [[ -n "${pg_sslrootcert}" ]]; then
  export PGSSLROOTCERT="${pg_sslrootcert}"
fi
export PGPASSWORD="${FLOWMESH_PG_PASSWORD:-}"
if [[ -z "${PGPASSWORD}" ]]; then
  require_value FLOWMESH_PG_PASSWORD "${PGPASSWORD}"
fi
pg_replication="$(run_with_timeout "${timeout_seconds}" psql \
  --host="${pg_host}" --port="${pg_port}" --username="${pg_user}" --dbname="${pg_database}" \
  --no-password --tuples-only --no-align \
  --command="SELECT pg_is_in_recovery()::text || '|' || (SELECT count(*)::text FROM pg_stat_replication);")"
if [[ ! "${pg_replication}" =~ ^f\|[0-9]+$ ]]; then
  echo 'PostgreSQL HA 预检失败：连接端点不是可写主库，或无法读取复制状态。' >&2
  exit 1
fi
pg_replica_count="${pg_replication##*|}"
if [[ "${pg_replica_count}" -lt "${expected_pg_replicas}" ]]; then
  echo "PostgreSQL 复制副本不足：实际 ${pg_replica_count}，期望至少 ${expected_pg_replicas}。" >&2
  exit 1
fi

redis_master_count=0
redis_master_host=''
redis_replica_count=0
redis_evidence=()
for endpoint in "${redis_endpoints[@]}"; do
  parse_endpoint "${endpoint}"
  redis_cli=(redis-cli --host "${PREFLIGHT_ENDPOINT_HOST}" --port "${PREFLIGHT_ENDPOINT_PORT}" --user "${FLOWMESH_REDIS_USER:-default}"
    --tls --connect-timeout "${timeout_seconds}" --socket-timeout "${timeout_seconds}" --no-auth-warning)
  if ! redis_info="$(REDISCLI_AUTH="${redis_password}" run_with_timeout "${timeout_seconds}" "${redis_cli[@]}" INFO replication)"; then
    echo "Redis HA 预检失败：${endpoint}" >&2
    exit 1
  fi
  redis_role="$(printf '%s\n' "${redis_info}" | awk -F: '$1 == "role" {print $2}' | tr -d '\r')"
  connected_slaves="$(printf '%s\n' "${redis_info}" | awk -F: '$1 == "connected_slaves" {print $2}' | tr -d '\r')"
  [[ "${connected_slaves}" =~ ^[0-9]+$ ]] || connected_slaves=0
  if [[ "${redis_role}" == master ]]; then
    redis_master_count=$((redis_master_count + 1))
    redis_master_host="${endpoint}"
    redis_replica_count="${connected_slaves}"
  fi
  redis_evidence+=("${endpoint}=${redis_role}/connected_slaves:${connected_slaves}")
done
if [[ "${redis_master_count}" -ne 1 ]]; then
  echo "Redis HA 预检要求恰好一个可观测主节点，实际：${redis_master_count}。" >&2
  exit 1
fi
if [[ "${redis_replica_count}" -lt "${expected_redis_replicas}" ]]; then
  echo "Redis 复制副本不足：实际 ${redis_replica_count}，期望至少 ${expected_redis_replicas}。" >&2
  exit 1
fi

rocketmq_evidence=()
for nameserver in "${nameservers[@]}"; do
  parse_endpoint "${nameserver}"
  tls_arguments=(-brief -connect "${PREFLIGHT_ENDPOINT_HOST}:${PREFLIGHT_ENDPOINT_PORT}")
  if [[ -n "${rocketmq_ca_file}" ]]; then
    tls_arguments+=(-CAfile "${rocketmq_ca_file}" -verify_return_error)
  fi
  if ! run_with_timeout "${timeout_seconds}" openssl s_client "${tls_arguments[@]}" </dev/null >/dev/null 2>&1; then
    echo "RocketMQ NameServer TLS 预检失败：${nameserver}" >&2
    exit 1
  fi
  rocketmq_evidence+=("${nameserver}=TLS_OK")
done

http_status="$(curl --silent --show-error --location --head --proto '=https' --proto-redir '=https' \
  --connect-timeout "${timeout_seconds}" --max-time "${timeout_seconds}" \
  --output /dev/null --write-out '%{http_code}' "${object_storage_endpoint}")"
case "${http_status}" in
  2*|3*|401|403|404|405) ;;
  *)
    echo "对象存储 HTTPS 预检失败：${object_storage_endpoint} 返回 HTTP ${http_status}" >&2
    exit 1
    ;;
esac

unset PGSSLMODE PGSSLROOTCERT PGPASSWORD REDISCLI_AUTH
{
  echo '# FlowMesh 外部依赖 HA 拓扑预检报告'
  echo
  echo "- 开始时间（UTC）：${started_at}"
  echo "- PostgreSQL：主库可写，观察到复制副本 ${pg_replica_count} 个（目标至少 ${expected_pg_replicas}）"
  echo "- Redis：主节点 ${redis_master_host}，观察到复制副本 ${redis_replica_count} 个（目标至少 ${expected_redis_replicas}）"
  echo "- Redis 端点：$(IFS=', '; echo "${redis_evidence[*]}")"
  echo "- RocketMQ NameServer：$(IFS=', '; echo "${rocketmq_evidence[*]}")"
  echo "- 对象存储：HTTPS 可达（HTTP ${http_status}）"
  echo '- 结果：PASS'
  echo
  echo '> 本报告只证明预检时刻的拓扑可见性和传输安全，不证明故障切换、数据复制完整性、备份恢复或 RTO/RPO；这些必须另行演练。'
} > "${temp_report}"

if ! (
  set -o noclobber
  cat "${temp_report}" > "${report_path}"
); then
  echo "无法创建生产 HA 报告，拒绝覆盖已有文件：${report_path}" >&2
  exit 64
fi
echo "生产 HA 拓扑预检通过，报告已保存：${report_path}"
cat "${report_path}"

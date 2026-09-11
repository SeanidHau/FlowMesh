#!/usr/bin/env bash
# 作用：在目标生产网络内只读检查外部依赖的地址、传输安全和基础连通性。
# 脚本不执行迁移、写入、故障切换或任何数据变更；HA/备份恢复仍需单独演练。
set -euo pipefail

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    printf '生产依赖预检缺少命令：%s\n' "$1" >&2
    exit 127
  }
}

require_value() {
  local name="$1"
  local value="$2"
  [[ -n "${value}" ]] || {
    printf '生产依赖预检缺少环境变量：%s\n' "${name}" >&2
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
    printf '生产依赖预检需要 timeout、gtimeout 或 perl 之一。\n' >&2
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

timeout_seconds="${FLOWMESH_PREFLIGHT_TIMEOUT_SECONDS:-5}"
pg_host="${FLOWMESH_PG_HOST:-}"
pg_port="${FLOWMESH_PG_PORT:-5432}"
pg_database="${FLOWMESH_PG_DATABASE:-flowmesh}"
pg_user="${FLOWMESH_PG_USER:-}"
pg_sslmode="${FLOWMESH_PG_SSLMODE:-require}"
redis_host="${FLOWMESH_REDIS_HOST:-}"
redis_port="${FLOWMESH_REDIS_PORT:-6379}"
redis_user="${FLOWMESH_REDIS_USER:-default}"
redis_password="${FLOWMESH_REDIS_PASSWORD:-}"
redis_tls="${FLOWMESH_REDIS_TLS:-true}"
namesrv_addresses="${FLOWMESH_ROCKETMQ_NAMESRV_ADDR:-}"
rocketmq_tls="${FLOWMESH_ROCKETMQ_TLS:-true}"
object_storage_endpoint="${FLOWMESH_OBJECT_STORAGE_ENDPOINT:-}"

require_value FLOWMESH_PG_HOST "${pg_host}"
require_value FLOWMESH_PG_USER "${pg_user}"
require_value FLOWMESH_REDIS_HOST "${redis_host}"
require_value FLOWMESH_REDIS_PASSWORD "${redis_password}"
require_value FLOWMESH_ROCKETMQ_NAMESRV_ADDR "${namesrv_addresses}"
require_value FLOWMESH_OBJECT_STORAGE_ENDPOINT "${object_storage_endpoint}"

case "${pg_sslmode}" in
  require|verify-ca|verify-full)
    ;;
  *)
    printf '生产依赖预检要求 PostgreSQL 使用 require、verify-ca 或 verify-full，当前为：%s\n' "${pg_sslmode}" >&2
    exit 2
    ;;
esac
[[ "${redis_tls}" == "true" ]] || {
  printf '生产依赖预检要求 Redis TLS_ENABLED=true。\n' >&2
  exit 2
}
[[ "${rocketmq_tls}" == "true" ]] || {
  printf '生产依赖预检要求 RocketMQ TLS=true。\n' >&2
  exit 2
}
[[ "${object_storage_endpoint}" == https://* ]] || {
  printf '生产依赖预检要求对象存储使用 HTTPS。\n' >&2
  exit 2
}

require_command pg_isready
require_command redis-cli
require_command openssl
require_command curl
require_command mktemp

tls_error_file="$(mktemp "${TMPDIR:-/tmp}/flowmesh-rocketmq-tls.XXXXXX")"
trap 'rm -f -- "${tls_error_file}"' EXIT

export PGSSLMODE="${pg_sslmode}"
if ! run_with_timeout "${timeout_seconds}" pg_isready \
  --host="${pg_host}" --port="${pg_port}" --username="${pg_user}" --dbname="${pg_database}" \
  --timeout="${timeout_seconds}" >/dev/null; then
  printf 'PostgreSQL readiness 预检失败：%s:%s\n' "${pg_host}" "${pg_port}" >&2
  exit 1
fi

redis_command=(redis-cli --host "${redis_host}" --port "${redis_port}" --user "${redis_user}"
  --tls --connect-timeout "${timeout_seconds}" --socket-timeout "${timeout_seconds}" --no-auth-warning PING)
if ! REDISCLI_AUTH="${redis_password}" run_with_timeout "${timeout_seconds}" "${redis_command[@]}" | grep -qx PONG; then
  printf 'Redis TLS/PING 预检失败：%s:%s\n' "${redis_host}" "${redis_port}" >&2
  exit 1
fi

IFS=',' read -r -a nameservers <<< "${namesrv_addresses}"
for nameserver in "${nameservers[@]}"; do
  nameserver="$(printf '%s' "${nameserver}" | tr -d '[:space:]')"
  parse_endpoint "${nameserver}"
  tls_arguments=(-brief -connect "${PREFLIGHT_ENDPOINT_HOST}:${PREFLIGHT_ENDPOINT_PORT}")
  if [[ -n "${FLOWMESH_ROCKETMQ_CA_FILE:-}" ]]; then
    tls_arguments+=(
      -CAfile "${FLOWMESH_ROCKETMQ_CA_FILE}"
      -verify_return_error
    )
  fi
  if ! run_with_timeout "${timeout_seconds}" openssl s_client "${tls_arguments[@]}" </dev/null \
    >/dev/null 2>"${tls_error_file}"; then
    printf 'RocketMQ NameServer TLS 预检失败：%s\n' "${nameserver}" >&2
    exit 1
  fi
done

http_status="$(curl --silent --show-error --location --head --proto '=https' --proto-redir '=https' \
  --connect-timeout "${timeout_seconds}" --max-time "${timeout_seconds}" \
  --output /dev/null --write-out '%{http_code}' "${object_storage_endpoint}")"
case "${http_status}" in
  2*|3*|401|403|404|405)
    ;;
  *)
    printf '对象存储 HTTPS 预检失败：%s 返回 HTTP %s\n' "${object_storage_endpoint}" "${http_status}" >&2
    exit 1
    ;;
esac

unset PGSSLMODE REDISCLI_AUTH
printf '生产依赖预检通过：PostgreSQL TLS、Redis TLS/PING、RocketMQ NameServer TLS、对象存储 HTTPS。\n'

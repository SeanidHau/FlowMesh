#!/usr/bin/env bash
# 作用：按统一保留策略批量清理已完成的消息记录、死信、重放审计、Inbox 和幂等记录。
# 该脚本只删除终态或幂等窗口已结束的数据，不删除待发送 Outbox、业务数据、审批快照和审计事件。

set -Eeuo pipefail

if [[ "${FLOWMESH_RETENTION_CONFIRM:-}" != "YES" ]]; then
  echo 'FLOWMESH_RETENTION_CONFIRM 必须精确设置为 YES，才允许执行数据清理。' >&2
  exit 64
fi

validate_positive_integer() {
  local name="$1"
  local value="$2"
  if [[ ! "${value}" =~ ^[1-9][0-9]*$ ]]; then
    echo "${name} 必须是正整数。" >&2
    exit 64
  fi
}

host="${FLOWMESH_PG_HOST:-localhost}"
port="${FLOWMESH_PG_PORT:-5432}"
database="${FLOWMESH_PG_DATABASE:-flowmesh}"
user="${FLOWMESH_RETENTION_DB_USER:-flowmesh_retention}"
password="${FLOWMESH_RETENTION_DB_PASSWORD:-}"
sslmode="${FLOWMESH_PG_SSLMODE:-require}"
connect_timeout="${FLOWMESH_PG_CONNECT_TIMEOUT_SECONDS:-5}"
outbox_retention_days="${FLOWMESH_RETENTION_OUTBOX_DAYS:-90}"
dlq_retention_days="${FLOWMESH_RETENTION_DLQ_DAYS:-30}"
inbox_retention_days="${FLOWMESH_RETENTION_INBOX_DAYS:-90}"
batch_size="${FLOWMESH_RETENTION_BATCH_SIZE:-1000}"

if [[ -z "${password}" ]]; then
  echo 'FLOWMESH_RETENTION_DB_PASSWORD 必须设置。' >&2
  exit 64
fi
validate_positive_integer FLOWMESH_RETENTION_OUTBOX_DAYS "${outbox_retention_days}"
validate_positive_integer FLOWMESH_RETENTION_DLQ_DAYS "${dlq_retention_days}"
validate_positive_integer FLOWMESH_RETENTION_INBOX_DAYS "${inbox_retention_days}"
validate_positive_integer FLOWMESH_RETENTION_BATCH_SIZE "${batch_size}"

case "${sslmode}" in
  disable|allow|prefer|require|verify-ca|verify-full) ;;
  *)
    echo 'FLOWMESH_PG_SSLMODE 不是 PostgreSQL 支持的连接模式。' >&2
    exit 64
    ;;
esac

if ! command -v psql >/dev/null 2>&1; then
  echo '未找到 psql，请使用包含 PostgreSQL 客户端的维护镜像或安装客户端。' >&2
  exit 127
fi

sql_file="$(mktemp)"
cleanup() {
  rm -f -- "${sql_file}"
  unset PGPASSWORD
}
trap cleanup EXIT

export PGPASSWORD="${password}"
psql_args=(
  --no-psqlrc
  --quiet
  --tuples-only
  --no-align
  --set ON_ERROR_STOP=1
  --set outbox_retention_days="${outbox_retention_days}"
  --set dlq_retention_days="${dlq_retention_days}"
  --set inbox_retention_days="${inbox_retention_days}"
  --set batch_size="${batch_size}"
  --host "${host}"
  --port "${port}"
  --username "${user}"
  --dbname "${database}"
)

export PGSSLMODE="${sslmode}"
export PGCONNECT_TIMEOUT="${connect_timeout}"

role_status="$(psql "${psql_args[@]}" --command "SELECT CASE WHEN rolsuper = false AND rolbypassrls = true THEN 'ok' ELSE 'reject' END FROM pg_roles WHERE rolname = current_user;")"
if [[ "$(printf '%s' "${role_status}" | tr -d '[:space:]')" != "ok" ]]; then
  echo '清理账号必须是非超级用户且显式具备 BYPASSRLS 的专用维护角色。' >&2
  exit 77
fi

lock_status="$(psql "${psql_args[@]}" --command 'SELECT pg_try_advisory_lock(776639201);')"
if [[ "$(printf '%s' "${lock_status}" | tr -d '[:space:]')" != "t" ]]; then
  echo '已有另一个 FlowMesh 数据清理任务运行，本次跳过。' >&2
  exit 75
fi

cat >"${sql_file}" <<'SQL'
-- 作用：使用固定表名和批量上限清理可安全删除的数据，避免动态 SQL 扩大删除范围。
SELECT set_config('flowmesh.outbox_retention_days', :'outbox_retention_days', false) AS ignored \gset
SELECT set_config('flowmesh.dlq_retention_days', :'dlq_retention_days', false) AS ignored \gset
SELECT set_config('flowmesh.inbox_retention_days', :'inbox_retention_days', false) AS ignored \gset
SELECT set_config('flowmesh.batch_size', :'batch_size', false) AS ignored \gset
DO $$
DECLARE
  deleted_rows BIGINT;
  total_rows BIGINT;
BEGIN
  total_rows := 0;
  LOOP
    WITH victim AS (
      SELECT id
        FROM supplier.supplier_outbox_events
       WHERE published_at IS NOT NULL
         AND dead_lettered_at IS NULL
         AND published_at < now() - make_interval(days => current_setting('flowmesh.outbox_retention_days')::int)
       ORDER BY published_at, id
       FOR UPDATE SKIP LOCKED
       LIMIT current_setting('flowmesh.batch_size')::int
    )
    DELETE FROM supplier.supplier_outbox_events target
     USING victim
     WHERE target.id = victim.id;
    GET DIAGNOSTICS deleted_rows = ROW_COUNT;
    total_rows := total_rows + deleted_rows;
    EXIT WHEN deleted_rows = 0;
  END LOOP;
  RAISE NOTICE 'supplier published outbox deleted: %', total_rows;
END $$;

DO $$
DECLARE
  deleted_rows BIGINT;
  total_rows BIGINT;
BEGIN
  total_rows := 0;
  LOOP
    WITH victim AS (
      SELECT id
        FROM supplier.supplier_outbox_events
       WHERE dead_lettered_at IS NOT NULL
         AND dead_lettered_at < now() - make_interval(days => current_setting('flowmesh.dlq_retention_days')::int)
       ORDER BY dead_lettered_at, id
       FOR UPDATE SKIP LOCKED
       LIMIT current_setting('flowmesh.batch_size')::int
    )
    DELETE FROM supplier.supplier_outbox_events target
     USING victim
     WHERE target.id = victim.id;
    GET DIAGNOSTICS deleted_rows = ROW_COUNT;
    total_rows := total_rows + deleted_rows;
    EXIT WHEN deleted_rows = 0;
  END LOOP;
  RAISE NOTICE 'supplier dead-letter outbox deleted: %', total_rows;
END $$;

DO $$
DECLARE
  deleted_rows BIGINT;
  total_rows BIGINT;
BEGIN
  total_rows := 0;
  LOOP
    WITH victim AS (
      SELECT id
        FROM supplier.supplier_outbox_replay_audits
       WHERE created_at < now() - make_interval(days => current_setting('flowmesh.outbox_retention_days')::int)
       ORDER BY created_at, id
       FOR UPDATE SKIP LOCKED
       LIMIT current_setting('flowmesh.batch_size')::int
    )
    DELETE FROM supplier.supplier_outbox_replay_audits target
     USING victim
     WHERE target.id = victim.id;
    GET DIAGNOSTICS deleted_rows = ROW_COUNT;
    total_rows := total_rows + deleted_rows;
    EXIT WHEN deleted_rows = 0;
  END LOOP;
  RAISE NOTICE 'supplier replay audits deleted: %', total_rows;
END $$;

DO $$
DECLARE
  deleted_rows BIGINT;
  total_rows BIGINT;
BEGIN
  total_rows := 0;
  LOOP
    WITH victim AS (
      SELECT event_id
        FROM supplier.supplier_workflow_event_inbox
       WHERE created_at < now() - make_interval(days => current_setting('flowmesh.inbox_retention_days')::int)
       ORDER BY created_at, event_id
       FOR UPDATE SKIP LOCKED
       LIMIT current_setting('flowmesh.batch_size')::int
    )
    DELETE FROM supplier.supplier_workflow_event_inbox target
     USING victim
     WHERE target.event_id = victim.event_id;
    GET DIAGNOSTICS deleted_rows = ROW_COUNT;
    total_rows := total_rows + deleted_rows;
    EXIT WHEN deleted_rows = 0;
  END LOOP;
  RAISE NOTICE 'supplier workflow inbox deleted: %', total_rows;
END $$;

DO $$
DECLARE
  deleted_rows BIGINT;
  total_rows BIGINT;
BEGIN
  total_rows := 0;
  LOOP
    WITH victim AS (
      SELECT id
        FROM supplier.supplier_idempotency_keys
       WHERE created_at < now() - make_interval(days => current_setting('flowmesh.inbox_retention_days')::int)
       ORDER BY created_at, id
       FOR UPDATE SKIP LOCKED
       LIMIT current_setting('flowmesh.batch_size')::int
    )
    DELETE FROM supplier.supplier_idempotency_keys target
     USING victim
     WHERE target.id = victim.id;
    GET DIAGNOSTICS deleted_rows = ROW_COUNT;
    total_rows := total_rows + deleted_rows;
    EXIT WHEN deleted_rows = 0;
  END LOOP;
  RAISE NOTICE 'supplier idempotency keys deleted: %', total_rows;
END $$;

DO $$
DECLARE
  deleted_rows BIGINT;
  total_rows BIGINT;
BEGIN
  total_rows := 0;
  LOOP
    WITH victim AS (
      SELECT id
        FROM workflow.workflow_outbox_events
       WHERE published_at IS NOT NULL
         AND dead_lettered_at IS NULL
         AND published_at < now() - make_interval(days => current_setting('flowmesh.outbox_retention_days')::int)
       ORDER BY published_at, id
       FOR UPDATE SKIP LOCKED
       LIMIT current_setting('flowmesh.batch_size')::int
    )
    DELETE FROM workflow.workflow_outbox_events target
     USING victim
     WHERE target.id = victim.id;
    GET DIAGNOSTICS deleted_rows = ROW_COUNT;
    total_rows := total_rows + deleted_rows;
    EXIT WHEN deleted_rows = 0;
  END LOOP;
  RAISE NOTICE 'workflow published outbox deleted: %', total_rows;
END $$;

DO $$
DECLARE
  deleted_rows BIGINT;
  total_rows BIGINT;
BEGIN
  total_rows := 0;
  LOOP
    WITH victim AS (
      SELECT id
        FROM workflow.workflow_outbox_events
       WHERE dead_lettered_at IS NOT NULL
         AND dead_lettered_at < now() - make_interval(days => current_setting('flowmesh.dlq_retention_days')::int)
       ORDER BY dead_lettered_at, id
       FOR UPDATE SKIP LOCKED
       LIMIT current_setting('flowmesh.batch_size')::int
    )
    DELETE FROM workflow.workflow_outbox_events target
     USING victim
     WHERE target.id = victim.id;
    GET DIAGNOSTICS deleted_rows = ROW_COUNT;
    total_rows := total_rows + deleted_rows;
    EXIT WHEN deleted_rows = 0;
  END LOOP;
  RAISE NOTICE 'workflow dead-letter outbox deleted: %', total_rows;
END $$;

DO $$
DECLARE
  deleted_rows BIGINT;
  total_rows BIGINT;
BEGIN
  total_rows := 0;
  LOOP
    WITH victim AS (
      SELECT id
        FROM workflow.workflow_outbox_replay_audits
       WHERE created_at < now() - make_interval(days => current_setting('flowmesh.outbox_retention_days')::int)
       ORDER BY created_at, id
       FOR UPDATE SKIP LOCKED
       LIMIT current_setting('flowmesh.batch_size')::int
    )
    DELETE FROM workflow.workflow_outbox_replay_audits target
     USING victim
     WHERE target.id = victim.id;
    GET DIAGNOSTICS deleted_rows = ROW_COUNT;
    total_rows := total_rows + deleted_rows;
    EXIT WHEN deleted_rows = 0;
  END LOOP;
  RAISE NOTICE 'workflow replay audits deleted: %', total_rows;
END $$;

DO $$
DECLARE
  deleted_rows BIGINT;
  total_rows BIGINT;
BEGIN
  total_rows := 0;
  LOOP
    WITH victim AS (
      SELECT event_id
        FROM workflow.workflow_risk_event_inbox
       WHERE created_at < now() - make_interval(days => current_setting('flowmesh.inbox_retention_days')::int)
       ORDER BY created_at, event_id
       FOR UPDATE SKIP LOCKED
       LIMIT current_setting('flowmesh.batch_size')::int
    )
    DELETE FROM workflow.workflow_risk_event_inbox target
     USING victim
     WHERE target.event_id = victim.event_id;
    GET DIAGNOSTICS deleted_rows = ROW_COUNT;
    total_rows := total_rows + deleted_rows;
    EXIT WHEN deleted_rows = 0;
  END LOOP;
  RAISE NOTICE 'workflow risk inbox deleted: %', total_rows;
END $$;

DO $$
DECLARE
  deleted_rows BIGINT;
  total_rows BIGINT;
BEGIN
  total_rows := 0;
  LOOP
    WITH victim AS (
      SELECT id
        FROM risk.risk_outbox_events
       WHERE published_at IS NOT NULL
         AND dead_lettered_at IS NULL
         AND published_at < now() - make_interval(days => current_setting('flowmesh.outbox_retention_days')::int)
       ORDER BY published_at, id
       FOR UPDATE SKIP LOCKED
       LIMIT current_setting('flowmesh.batch_size')::int
    )
    DELETE FROM risk.risk_outbox_events target
     USING victim
     WHERE target.id = victim.id;
    GET DIAGNOSTICS deleted_rows = ROW_COUNT;
    total_rows := total_rows + deleted_rows;
    EXIT WHEN deleted_rows = 0;
  END LOOP;
  RAISE NOTICE 'risk published outbox deleted: %', total_rows;
END $$;

DO $$
DECLARE
  deleted_rows BIGINT;
  total_rows BIGINT;
BEGIN
  total_rows := 0;
  LOOP
    WITH victim AS (
      SELECT id
        FROM risk.risk_outbox_events
       WHERE dead_lettered_at IS NOT NULL
         AND dead_lettered_at < now() - make_interval(days => current_setting('flowmesh.dlq_retention_days')::int)
       ORDER BY dead_lettered_at, id
       FOR UPDATE SKIP LOCKED
       LIMIT current_setting('flowmesh.batch_size')::int
    )
    DELETE FROM risk.risk_outbox_events target
     USING victim
     WHERE target.id = victim.id;
    GET DIAGNOSTICS deleted_rows = ROW_COUNT;
    total_rows := total_rows + deleted_rows;
    EXIT WHEN deleted_rows = 0;
  END LOOP;
  RAISE NOTICE 'risk dead-letter outbox deleted: %', total_rows;
END $$;

DO $$
DECLARE
  deleted_rows BIGINT;
  total_rows BIGINT;
BEGIN
  total_rows := 0;
  LOOP
    WITH victim AS (
      SELECT event_id
        FROM audit.audit_event_inbox
       WHERE created_at < now() - make_interval(days => current_setting('flowmesh.inbox_retention_days')::int)
       ORDER BY created_at, event_id
       FOR UPDATE SKIP LOCKED
       LIMIT current_setting('flowmesh.batch_size')::int
    )
    DELETE FROM audit.audit_event_inbox target
     USING victim
     WHERE target.event_id = victim.event_id;
    GET DIAGNOSTICS deleted_rows = ROW_COUNT;
    total_rows := total_rows + deleted_rows;
    EXIT WHEN deleted_rows = 0;
  END LOOP;
  RAISE NOTICE 'audit event inbox deleted: %', total_rows;
END $$;
SQL

psql "${psql_args[@]}" --file "${sql_file}"
echo "FlowMesh 数据生命周期清理完成：outbox=${outbox_retention_days}d，DLQ=${dlq_retention_days}d，Inbox/idempotency=${inbox_retention_days}d。"

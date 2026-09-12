#!/usr/bin/env bash
# 作用：只读核验生命周期维护账号的角色属性、RLS 能力和最小表列权限。
# 脚本不执行迁移、写入或删除；它用于生产部署前预检和生命周期 E2E。
set -Eeuo pipefail

require_value() {
  local name="$1"
  local value="$2"
  [[ -n "${value}" ]] || {
    printf '生命周期角色预检缺少环境变量：%s\n' "${name}" >&2
    exit 2
  }
}

host="${FLOWMESH_PG_HOST:-localhost}"
port="${FLOWMESH_PG_PORT:-5432}"
database="${FLOWMESH_PG_DATABASE:-flowmesh}"
user="${FLOWMESH_RETENTION_DB_USER:-flowmesh_retention}"
password="${FLOWMESH_RETENTION_DB_PASSWORD:-}"
sslmode="${FLOWMESH_PG_SSLMODE:-require}"
connect_timeout="${FLOWMESH_PG_CONNECT_TIMEOUT_SECONDS:-5}"

require_value FLOWMESH_RETENTION_DB_PASSWORD "${password}"

case "${sslmode}" in
  disable|allow|prefer|require|verify-ca|verify-full) ;;
  *)
    printf 'FLOWMESH_PG_SSLMODE 不是 PostgreSQL 支持的连接模式：%s\n' "${sslmode}" >&2
    exit 2
    ;;
esac

command -v psql >/dev/null 2>&1 || {
  echo '未找到 psql，请使用 PostgreSQL 客户端执行生命周期角色预检。' >&2
  exit 127
}

cleanup() {
  unset PGPASSWORD PGSSLMODE PGSSLROOTCERT PGCONNECT_TIMEOUT
}
trap cleanup EXIT

export PGPASSWORD="${password}"
export PGSSLMODE="${sslmode}"
export PGCONNECT_TIMEOUT="${connect_timeout}"
if [[ -n "${FLOWMESH_PG_SSLROOTCERT:-}" ]]; then
  export PGSSLROOTCERT="${FLOWMESH_PG_SSLROOTCERT}"
fi

# psql 变量不能直接在 DO $$...$$ 内展开，因此先写入本连接会话参数，再由 PL/pgSQL 读取。
psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 \
  --set retention_user="${user}" \
  --host "${host}" --port "${port}" --username "${user}" --dbname "${database}" <<'SQL'
SELECT set_config('flowmesh.retention_user', :'retention_user', false) AS ignored \gset

DO $$
DECLARE
  retention_role_name TEXT := current_setting('flowmesh.retention_user');
  retention_role_oid OID;
  role_record RECORD;
  table_record RECORD;
  column_record RECORD;
  qualified_table TEXT;
BEGIN
  SELECT oid, rolsuper, rolcreatedb, rolcreaterole, rolcanlogin, rolinherit, rolbypassrls
    INTO role_record
    FROM pg_roles
   WHERE rolname = retention_role_name;

  IF NOT FOUND THEN
    RAISE EXCEPTION '生命周期维护角色不存在：%', retention_role_name;
  END IF;
  retention_role_oid := role_record.oid;

  IF role_record.rolsuper OR role_record.rolcreatedb OR role_record.rolcreaterole
     OR NOT role_record.rolcanlogin OR role_record.rolinherit
     OR NOT role_record.rolbypassrls THEN
    RAISE EXCEPTION '生命周期维护角色属性不符合最小权限要求：%', retention_role_name;
  END IF;

  IF EXISTS (
    SELECT 1
      FROM pg_auth_members
     WHERE member = retention_role_oid
  ) THEN
    RAISE EXCEPTION '生命周期维护角色不应继承或可切换到其他角色：%', retention_role_name;
  END IF;

  FOR table_record IN
    SELECT *
      FROM (VALUES
        ('supplier', 'supplier_outbox_events', 'id'),
        ('supplier', 'supplier_outbox_replay_audits', 'id'),
        ('supplier', 'supplier_workflow_event_inbox', 'event_id'),
        ('supplier', 'supplier_idempotency_keys', 'id'),
        ('workflow', 'workflow_outbox_events', 'id'),
        ('workflow', 'workflow_outbox_replay_audits', 'id'),
        ('workflow', 'workflow_risk_event_inbox', 'event_id'),
        ('risk', 'risk_outbox_events', 'id'),
        ('audit', 'audit_event_inbox', 'event_id'),
        ('audit', 'notification_deliveries', 'id')
      ) AS expected(schema_name, table_name, lock_column)
  LOOP
    qualified_table := format('%I.%I', table_record.schema_name, table_record.table_name);

    IF to_regclass(qualified_table) IS NULL THEN
      RAISE EXCEPTION '生命周期清理目标表不存在：%', qualified_table;
    END IF;
    IF NOT has_schema_privilege(retention_role_name, table_record.schema_name, 'USAGE')
       OR has_schema_privilege(retention_role_name, table_record.schema_name, 'CREATE') THEN
      RAISE EXCEPTION '生命周期维护角色的 Schema 权限不符合要求：%', table_record.schema_name;
    END IF;
    IF NOT has_table_privilege(retention_role_name, qualified_table, 'SELECT')
       OR NOT has_table_privilege(retention_role_name, qualified_table, 'DELETE') THEN
      RAISE EXCEPTION '生命周期维护角色缺少 SELECT/DELETE 权限：%', qualified_table;
    END IF;
    IF has_table_privilege(retention_role_name, qualified_table, 'INSERT')
       OR has_table_privilege(retention_role_name, qualified_table, 'TRUNCATE')
       OR has_table_privilege(retention_role_name, qualified_table, 'REFERENCES')
       OR has_table_privilege(retention_role_name, qualified_table, 'TRIGGER') THEN
      RAISE EXCEPTION '生命周期维护角色拥有不必要的表级写权限：%', qualified_table;
    END IF;
    IF EXISTS (
      SELECT 1
        FROM pg_class relation
       WHERE relation.oid = to_regclass(qualified_table)
         AND relation.relowner = retention_role_oid
    ) THEN
      RAISE EXCEPTION '生命周期维护角色不应拥有清理目标表：%', qualified_table;
    END IF;

    IF NOT has_column_privilege(retention_role_name, qualified_table, table_record.lock_column, 'UPDATE') THEN
      RAISE EXCEPTION '生命周期维护角色缺少行锁键列 UPDATE 权限：%.%', qualified_table, table_record.lock_column;
    END IF;

    FOR column_record IN
      SELECT column_name
        FROM information_schema.columns
       WHERE table_schema = table_record.schema_name
         AND table_name = table_record.table_name
         AND column_name <> table_record.lock_column
    LOOP
      IF has_column_privilege(retention_role_name, qualified_table, column_record.column_name, 'UPDATE') THEN
        RAISE EXCEPTION '生命周期维护角色拥有非锁键列 UPDATE 权限：%.%', qualified_table, column_record.column_name;
      END IF;
    END LOOP;
  END LOOP;

  IF EXISTS (
    SELECT 1
      FROM pg_class relation
      JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
     WHERE namespace.nspname IN ('iam', 'supplier', 'workflow', 'risk', 'audit')
       AND relation.relkind IN ('r', 'p')
       AND has_table_privilege(retention_role_name, format('%I.%I', namespace.nspname, relation.relname), 'SELECT')
       AND NOT EXISTS (
         SELECT 1
           FROM (VALUES
             ('supplier', 'supplier_outbox_events'),
             ('supplier', 'supplier_outbox_replay_audits'),
             ('supplier', 'supplier_workflow_event_inbox'),
             ('supplier', 'supplier_idempotency_keys'),
             ('workflow', 'workflow_outbox_events'),
             ('workflow', 'workflow_outbox_replay_audits'),
             ('workflow', 'workflow_risk_event_inbox'),
             ('risk', 'risk_outbox_events'),
             ('audit', 'audit_event_inbox'),
             ('audit', 'notification_deliveries')
           ) AS expected(schema_name, table_name)
          WHERE expected.schema_name = namespace.nspname
            AND expected.table_name = relation.relname
       )
  ) THEN
    RAISE EXCEPTION '生命周期维护角色对清理白名单之外的业务表拥有 SELECT 权限';
  END IF;
END $$;
SQL

printf '生命周期维护角色预检通过：角色属性、RLS 能力、Schema 权限和清理白名单均符合要求。\n'

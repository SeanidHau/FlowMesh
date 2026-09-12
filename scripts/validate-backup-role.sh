#!/usr/bin/env bash
# 作用：只读核验 PostgreSQL 备份账号的最小权限和完整备份能力。
# 脚本不创建角色、不修改授权、不执行备份或恢复；它只检查目标账号的角色属性和现有权限。

set -Eeuo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/postgres-tls.sh"

require_value() {
  local name="$1"
  local value="$2"
  [[ -n "${value}" ]] || {
    printf '备份角色预检缺少环境变量：%s\n' "${name}" >&2
    exit 2
  }
}

host="${FLOWMESH_PG_HOST:-localhost}"
port="${FLOWMESH_PG_PORT:-5432}"
database="${FLOWMESH_PG_DATABASE:-flowmesh}"
user="${FLOWMESH_BACKUP_DB_USER:-${FLOWMESH_PG_USER:-flowmesh}}"
password="${FLOWMESH_BACKUP_DB_PASSWORD:-${FLOWMESH_PG_PASSWORD:-}}"
sslmode="${FLOWMESH_PG_SSLMODE:-require}"
connect_timeout="${FLOWMESH_PG_CONNECT_TIMEOUT_SECONDS:-5}"

require_value FLOWMESH_BACKUP_DB_PASSWORD_OR_FLOWMESH_PG_PASSWORD "${password}"
[[ "${user}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || {
  printf '备份角色名包含非法字符：%s\n' "${user}" >&2
  exit 2
}
case "${sslmode}" in
  disable|allow|prefer|require|verify-ca|verify-full) ;;
  *)
    printf 'FLOWMESH_PG_SSLMODE 不是 PostgreSQL 支持的连接模式：%s\n' "${sslmode}" >&2
    exit 2
    ;;
esac
flowmesh_require_postgres_ca "${sslmode}" "${FLOWMESH_PG_SSLROOTCERT:-}"

command -v psql >/dev/null 2>&1 || {
  echo '未找到 psql，请使用 PostgreSQL 客户端执行备份角色预检。' >&2
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

# 通过当前备份账号检查自身属性，避免预检账号拥有管理员权限却绕过真实备份账号边界。
psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 \
  --set backup_user="${user}" \
  --host "${host}" --port "${port}" --username "${user}" --dbname "${database}" <<'SQL'
SELECT set_config('flowmesh.backup_user', :'backup_user', false) AS ignored \gset

DO $$
DECLARE
  backup_role_name TEXT := current_setting('flowmesh.backup_user');
  backup_role_oid OID;
  role_record RECORD;
  schema_record RECORD;
  table_record RECORD;
  qualified_table TEXT;
BEGIN
  SELECT oid, rolsuper, rolcreatedb, rolcreaterole, rolcanlogin, rolreplication, rolbypassrls
    INTO role_record
    FROM pg_roles
   WHERE rolname = backup_role_name;

  IF NOT FOUND THEN
    RAISE EXCEPTION '备份角色不存在：%', backup_role_name;
  END IF;
  backup_role_oid := role_record.oid;

  IF role_record.rolsuper OR role_record.rolcreatedb OR role_record.rolcreaterole
     OR NOT role_record.rolcanlogin OR role_record.rolreplication OR NOT role_record.rolbypassrls THEN
    RAISE EXCEPTION '备份角色属性不符合最小权限或完整备份要求：%', backup_role_name;
  END IF;

  FOR schema_record IN
    SELECT schema_name
      FROM information_schema.schemata
     WHERE schema_name IN ('iam', 'supplier', 'workflow', 'risk', 'audit')
  LOOP
    IF NOT has_schema_privilege(backup_role_name, schema_record.schema_name, 'USAGE')
       OR has_schema_privilege(backup_role_name, schema_record.schema_name, 'CREATE') THEN
      RAISE EXCEPTION '备份角色 Schema 权限不符合要求：%', schema_record.schema_name;
    END IF;
  END LOOP;

  FOR table_record IN
    SELECT namespace.nspname AS schema_name, relation.relname AS table_name, relation.relowner
      FROM pg_class relation
      JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
     WHERE namespace.nspname IN ('iam', 'supplier', 'workflow', 'risk', 'audit')
       AND relation.relkind IN ('r', 'p')
  LOOP
    qualified_table := format('%I.%I', table_record.schema_name, table_record.table_name);
    IF NOT has_table_privilege(backup_role_name, qualified_table, 'SELECT') THEN
      RAISE EXCEPTION '备份角色缺少表 SELECT 权限：%', qualified_table;
    END IF;
    IF has_table_privilege(backup_role_name, qualified_table, 'INSERT')
       OR has_table_privilege(backup_role_name, qualified_table, 'UPDATE')
       OR has_table_privilege(backup_role_name, qualified_table, 'DELETE')
       OR has_table_privilege(backup_role_name, qualified_table, 'TRUNCATE')
       OR has_table_privilege(backup_role_name, qualified_table, 'REFERENCES')
       OR has_table_privilege(backup_role_name, qualified_table, 'TRIGGER') THEN
      RAISE EXCEPTION '备份角色拥有不必要的业务表写权限：%', qualified_table;
    END IF;
    IF table_record.relowner = backup_role_oid THEN
      RAISE EXCEPTION '备份角色不应拥有业务表：%', qualified_table;
    END IF;
  END LOOP;
END $$;
SQL

printf '备份角色预检通过：非超级用户、BYPASSRLS、业务表只读且具备完整租户数据备份能力。\n'

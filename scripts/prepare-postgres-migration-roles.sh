#!/usr/bin/env bash
# 作用：为已有 PostgreSQL 环境创建 Flyway 迁移账号，并把现有 Schema 对象所有权
# 安全地切换到迁移账号。脚本只允许由数据库管理员在发布前显式执行一次。

set -Eeuo pipefail

: "${FLOWMESH_ADMIN_PGURI:?FLOWMESH_ADMIN_PGURI must be provided}"
: "${FLOWMESH_ADMIN_DB_PASSWORD:?FLOWMESH_ADMIN_DB_PASSWORD must be provided}"
: "${IAM_DB_MIGRATOR_PASSWORD:?IAM_DB_MIGRATOR_PASSWORD must be provided}"
: "${SUPPLIER_DB_MIGRATOR_PASSWORD:?SUPPLIER_DB_MIGRATOR_PASSWORD must be provided}"
: "${WORKFLOW_DB_MIGRATOR_PASSWORD:?WORKFLOW_DB_MIGRATOR_PASSWORD must be provided}"
: "${RISK_DB_MIGRATOR_PASSWORD:?RISK_DB_MIGRATOR_PASSWORD must be provided}"
: "${AUDIT_DB_MIGRATOR_PASSWORD:?AUDIT_DB_MIGRATOR_PASSWORD must be provided}"

cleanup() {
  unset PGPASSWORD IAM_DB_MIGRATOR_PASSWORD SUPPLIER_DB_MIGRATOR_PASSWORD \
    WORKFLOW_DB_MIGRATOR_PASSWORD RISK_DB_MIGRATOR_PASSWORD AUDIT_DB_MIGRATOR_PASSWORD
}
trap cleanup EXIT

export PGPASSWORD="${FLOWMESH_ADMIN_DB_PASSWORD}"
export IAM_DB_MIGRATOR_PASSWORD SUPPLIER_DB_MIGRATOR_PASSWORD WORKFLOW_DB_MIGRATOR_PASSWORD
export RISK_DB_MIGRATOR_PASSWORD AUDIT_DB_MIGRATOR_PASSWORD

psql --set ON_ERROR_STOP=1 "${FLOWMESH_ADMIN_PGURI}" <<'SQL'
\getenv iam_migrator_password IAM_DB_MIGRATOR_PASSWORD
\getenv supplier_migrator_password SUPPLIER_DB_MIGRATOR_PASSWORD
\getenv workflow_migrator_password WORKFLOW_DB_MIGRATOR_PASSWORD
\getenv risk_migrator_password RISK_DB_MIGRATOR_PASSWORD
\getenv audit_migrator_password AUDIT_DB_MIGRATOR_PASSWORD

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'flowmesh_iam_migrator') THEN
        CREATE ROLE flowmesh_iam_migrator LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'flowmesh_supplier_migrator') THEN
        CREATE ROLE flowmesh_supplier_migrator LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'flowmesh_workflow_migrator') THEN
        CREATE ROLE flowmesh_workflow_migrator LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'flowmesh_risk_migrator') THEN
        CREATE ROLE flowmesh_risk_migrator LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'flowmesh_audit_migrator') THEN
        CREATE ROLE flowmesh_audit_migrator LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'flowmesh_audit_delivery') THEN
        CREATE ROLE flowmesh_audit_delivery NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT BYPASSRLS;
    END IF;
END
$$;

ALTER ROLE flowmesh_iam_migrator PASSWORD :'iam_migrator_password';
ALTER ROLE flowmesh_supplier_migrator PASSWORD :'supplier_migrator_password';
ALTER ROLE flowmesh_workflow_migrator PASSWORD :'workflow_migrator_password';
ALTER ROLE flowmesh_risk_migrator PASSWORD :'risk_migrator_password';
ALTER ROLE flowmesh_audit_migrator PASSWORD :'audit_migrator_password';
ALTER ROLE flowmesh_iam_migrator LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
ALTER ROLE flowmesh_supplier_migrator LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
ALTER ROLE flowmesh_workflow_migrator LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
ALTER ROLE flowmesh_risk_migrator LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
ALTER ROLE flowmesh_audit_migrator LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
ALTER ROLE flowmesh_audit_delivery NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT BYPASSRLS;
GRANT flowmesh_audit_delivery TO flowmesh_audit_migrator;

CREATE SCHEMA IF NOT EXISTS iam;
CREATE SCHEMA IF NOT EXISTS supplier;
CREATE SCHEMA IF NOT EXISTS workflow;
CREATE SCHEMA IF NOT EXISTS risk;
CREATE SCHEMA IF NOT EXISTS audit;

ALTER SCHEMA iam OWNER TO flowmesh_iam_migrator;
ALTER SCHEMA supplier OWNER TO flowmesh_supplier_migrator;
ALTER SCHEMA workflow OWNER TO flowmesh_workflow_migrator;
ALTER SCHEMA risk OWNER TO flowmesh_risk_migrator;
ALTER SCHEMA audit OWNER TO flowmesh_audit_migrator;

DO $$
DECLARE
    item RECORD;
    schema_name TEXT;
    owner_name TEXT;
BEGIN
    FOR schema_name, owner_name IN
        SELECT * FROM (VALUES
            ('iam', 'flowmesh_iam_migrator'),
            ('supplier', 'flowmesh_supplier_migrator'),
            ('workflow', 'flowmesh_workflow_migrator'),
            ('risk', 'flowmesh_risk_migrator'),
            ('audit', 'flowmesh_audit_migrator')
        ) AS roles(schema_name, owner_name)
    LOOP
        FOR item IN
            SELECT c.relname, c.relkind
              FROM pg_class c
              JOIN pg_namespace n ON n.oid = c.relnamespace
             WHERE n.nspname = schema_name
               AND c.relkind IN ('r', 'p', 'v', 'm', 'S')
               AND NOT (schema_name = 'audit' AND c.relname = 'notification_deliveries')
        LOOP
            IF item.relkind = 'S' THEN
                EXECUTE format('ALTER SEQUENCE %I.%I OWNER TO %I', schema_name, item.relname, owner_name);
            ELSIF item.relkind = 'v' THEN
                EXECUTE format('ALTER VIEW %I.%I OWNER TO %I', schema_name, item.relname, owner_name);
            ELSIF item.relkind = 'm' THEN
                EXECUTE format('ALTER MATERIALIZED VIEW %I.%I OWNER TO %I', schema_name, item.relname, owner_name);
            ELSE
                EXECUTE format('ALTER TABLE %I.%I OWNER TO %I', schema_name, item.relname, owner_name);
            END IF;
        END LOOP;

        -- 通知投递函数必须继续由 BYPASSRLS 角色拥有，不能被普通迁移账号接管。
        FOR item IN
            SELECT p.oid, p.proname, pg_get_function_identity_arguments(p.oid) AS arguments
              FROM pg_proc p
              JOIN pg_namespace n ON n.oid = p.pronamespace
              JOIN pg_roles r ON r.oid = p.proowner
             WHERE n.nspname = schema_name
               AND r.rolname <> 'flowmesh_audit_delivery'
        LOOP
            EXECUTE format(
                'ALTER FUNCTION %I.%I(%s) OWNER TO %I',
                schema_name, item.proname, item.arguments, owner_name
            );
        END LOOP;
    END LOOP;
END
$$;

GRANT USAGE, CREATE ON SCHEMA iam TO flowmesh_iam_migrator;
GRANT USAGE, CREATE ON SCHEMA supplier TO flowmesh_supplier_migrator;
GRANT USAGE, CREATE ON SCHEMA workflow TO flowmesh_workflow_migrator;
GRANT USAGE, CREATE ON SCHEMA risk TO flowmesh_risk_migrator;
GRANT USAGE, CREATE ON SCHEMA audit TO flowmesh_audit_migrator;

REVOKE CREATE ON SCHEMA iam FROM flowmesh_iam;
REVOKE CREATE ON SCHEMA supplier FROM flowmesh_supplier;
REVOKE CREATE ON SCHEMA workflow FROM flowmesh_workflow;
REVOKE CREATE ON SCHEMA risk FROM flowmesh_risk;
REVOKE CREATE ON SCHEMA audit FROM flowmesh_audit;
GRANT USAGE ON SCHEMA iam TO flowmesh_iam;
GRANT USAGE ON SCHEMA supplier TO flowmesh_supplier;
GRANT USAGE ON SCHEMA workflow TO flowmesh_workflow;
GRANT USAGE ON SCHEMA risk TO flowmesh_risk;
GRANT USAGE ON SCHEMA audit TO flowmesh_audit;

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA iam TO flowmesh_iam;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA supplier TO flowmesh_supplier;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA workflow TO flowmesh_workflow;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA risk TO flowmesh_risk;
GRANT SELECT, INSERT, UPDATE, DELETE ON audit.audit_event_inbox, audit.audit_events, audit.notifications TO flowmesh_audit;
DO $$
BEGIN
    IF to_regclass('audit.notification_deliveries') IS NOT NULL THEN
        EXECUTE 'REVOKE ALL ON audit.notification_deliveries FROM flowmesh_audit';
        EXECUTE 'GRANT INSERT ON audit.notification_deliveries TO flowmesh_audit';
    END IF;
END
$$;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA iam TO flowmesh_iam;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA supplier TO flowmesh_supplier;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA workflow TO flowmesh_workflow;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA risk TO flowmesh_risk;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA audit TO flowmesh_audit;

ALTER DEFAULT PRIVILEGES FOR ROLE flowmesh_iam_migrator IN SCHEMA iam
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO flowmesh_iam;
ALTER DEFAULT PRIVILEGES FOR ROLE flowmesh_supplier_migrator IN SCHEMA supplier
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO flowmesh_supplier;
ALTER DEFAULT PRIVILEGES FOR ROLE flowmesh_workflow_migrator IN SCHEMA workflow
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO flowmesh_workflow;
ALTER DEFAULT PRIVILEGES FOR ROLE flowmesh_risk_migrator IN SCHEMA risk
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO flowmesh_risk;
ALTER DEFAULT PRIVILEGES FOR ROLE flowmesh_audit_migrator IN SCHEMA audit
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO flowmesh_audit;
ALTER DEFAULT PRIVILEGES FOR ROLE flowmesh_iam_migrator IN SCHEMA iam
    GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO flowmesh_iam;
ALTER DEFAULT PRIVILEGES FOR ROLE flowmesh_supplier_migrator IN SCHEMA supplier
    GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO flowmesh_supplier;
ALTER DEFAULT PRIVILEGES FOR ROLE flowmesh_workflow_migrator IN SCHEMA workflow
    GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO flowmesh_workflow;
ALTER DEFAULT PRIVILEGES FOR ROLE flowmesh_risk_migrator IN SCHEMA risk
    GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO flowmesh_risk;
ALTER DEFAULT PRIVILEGES FOR ROLE flowmesh_audit_migrator IN SCHEMA audit
    GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO flowmesh_audit;
SQL

echo 'PostgreSQL Flyway migration roles prepared.'

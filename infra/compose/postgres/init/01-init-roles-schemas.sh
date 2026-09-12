#!/bin/bash
# 作用：初始化 FlowMesh 各服务的数据库角色和 Schema。
# Docker Compose 首次启动 PostgreSQL 时由 docker-entrypoint-initdb.d 自动执行。
# 密码来自 Compose 环境变量（.env）。

set -e

: "${IAM_DB_PASSWORD:?IAM_DB_PASSWORD must be provided}"
: "${IAM_DB_MIGRATOR_PASSWORD:?IAM_DB_MIGRATOR_PASSWORD must be provided}"
: "${SUPPLIER_DB_PASSWORD:?SUPPLIER_DB_PASSWORD must be provided}"
: "${SUPPLIER_DB_MIGRATOR_PASSWORD:?SUPPLIER_DB_MIGRATOR_PASSWORD must be provided}"
: "${WORKFLOW_DB_PASSWORD:?WORKFLOW_DB_PASSWORD must be provided}"
: "${WORKFLOW_DB_MIGRATOR_PASSWORD:?WORKFLOW_DB_MIGRATOR_PASSWORD must be provided}"
: "${WORKFLOW_SLA_DB_PASSWORD:?WORKFLOW_SLA_DB_PASSWORD must be provided}"
: "${RISK_DB_PASSWORD:?RISK_DB_PASSWORD must be provided}"
: "${RISK_DB_MIGRATOR_PASSWORD:?RISK_DB_MIGRATOR_PASSWORD must be provided}"
: "${AUDIT_DB_PASSWORD:?AUDIT_DB_PASSWORD must be provided}"
: "${AUDIT_DB_MIGRATOR_PASSWORD:?AUDIT_DB_MIGRATOR_PASSWORD must be provided}"
: "${RETENTION_DB_PASSWORD:?RETENTION_DB_PASSWORD must be provided}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
  -- 创建 IAM 服务业务账号（NOSUPERUSER，仅拥有 iam schema）
  DO \$\$
  BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'flowmesh_iam') THEN
      CREATE ROLE flowmesh_iam LOGIN PASSWORD '${IAM_DB_PASSWORD}' NOSUPERUSER;
    END IF;
  END \$\$;
  -- IAM 迁移账号只负责 DDL 和 Flyway 历史，不作为应用数据源账号使用。
  DO \$\$
  BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'flowmesh_iam_migrator') THEN
      CREATE ROLE flowmesh_iam_migrator LOGIN PASSWORD '${IAM_DB_MIGRATOR_PASSWORD}'
        NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
    END IF;
  END \$\$;
  ALTER ROLE flowmesh_iam_migrator LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;

  -- 创建 risk 服务业务账号（NOSUPERUSER，仅拥有 risk schema）
  DO \$\$
  BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'flowmesh_risk') THEN
      CREATE ROLE flowmesh_risk LOGIN PASSWORD '${RISK_DB_PASSWORD}' NOSUPERUSER;
    END IF;
  END \$\$;
  -- risk 迁移账号与运行时业务账号分离，避免应用进程持有 DDL 权限。
  DO \$\$
  BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'flowmesh_risk_migrator') THEN
      CREATE ROLE flowmesh_risk_migrator LOGIN PASSWORD '${RISK_DB_MIGRATOR_PASSWORD}'
        NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
    END IF;
  END \$\$;
  ALTER ROLE flowmesh_risk_migrator LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;

  -- 创建通知审计服务业务账号（NOSUPERUSER，仅拥有 audit schema）
  DO \$\$
  BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'flowmesh_audit') THEN
      CREATE ROLE flowmesh_audit LOGIN PASSWORD '${AUDIT_DB_PASSWORD}' NOSUPERUSER NOINHERIT;
    END IF;
  END \$\$;
  ALTER ROLE flowmesh_audit NOINHERIT;

  -- audit 迁移账号需要在 V3 中 SET ROLE 到独立的通知投递维护角色。
  DO \$\$
  BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'flowmesh_audit_migrator') THEN
      CREATE ROLE flowmesh_audit_migrator LOGIN PASSWORD '${AUDIT_DB_MIGRATOR_PASSWORD}'
        NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
    END IF;
  END \$\$;
  ALTER ROLE flowmesh_audit_migrator LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;

  -- 外部通知调度账号只用于投递队列安全函数；不允许登录或继承业务账号权限。
  DO \$\$
  BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'flowmesh_audit_delivery') THEN
      CREATE ROLE flowmesh_audit_delivery NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT BYPASSRLS;
    END IF;
  END \$\$;
  ALTER ROLE flowmesh_audit_delivery NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT BYPASSRLS;
  GRANT flowmesh_audit_delivery TO flowmesh_audit;

  -- 创建 supplier 服务业务账号（NOSUPERUSER，仅拥有 supplier schema）
  DO \$\$
  BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'flowmesh_supplier') THEN
      CREATE ROLE flowmesh_supplier LOGIN PASSWORD '${SUPPLIER_DB_PASSWORD}' NOSUPERUSER;
    END IF;
  END \$\$;

  -- supplier 迁移账号仅用于 Flyway DDL。
  DO \$\$
  BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'flowmesh_supplier_migrator') THEN
      CREATE ROLE flowmesh_supplier_migrator LOGIN PASSWORD '${SUPPLIER_DB_MIGRATOR_PASSWORD}'
        NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
    END IF;
  END \$\$;

  ALTER ROLE flowmesh_supplier_migrator LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;

  -- 创建 workflow 服务业务账号（NOSUPERUSER，仅拥有 workflow schema）
  DO \$\$
  BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'flowmesh_workflow') THEN
      CREATE ROLE flowmesh_workflow LOGIN PASSWORD '${WORKFLOW_DB_PASSWORD}' NOSUPERUSER;
    END IF;
  END \$\$;

  -- workflow 迁移账号仅用于 Flyway DDL。
  DO \$\$
  BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'flowmesh_workflow_migrator') THEN
      CREATE ROLE flowmesh_workflow_migrator LOGIN PASSWORD '${WORKFLOW_DB_MIGRATOR_PASSWORD}'
        NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
    END IF;
  END \$\$;

  ALTER ROLE flowmesh_workflow_migrator LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;

  -- Workflow SLA 维护账号只访问任务、流程实例和 Outbox，并显式绕过 RLS 执行跨租户扫描。
  DO \$\$
  BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'flowmesh_workflow_sla') THEN
      CREATE ROLE flowmesh_workflow_sla LOGIN PASSWORD '${WORKFLOW_SLA_DB_PASSWORD}'
        NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT BYPASSRLS;
    END IF;
  END \$\$;

  -- 生命周期维护账号仅绕过 RLS，不具备超级用户、建库或建角色权限；表级权限由各服务迁移授予。
  DO \$\$
  BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'flowmesh_retention') THEN
      CREATE ROLE flowmesh_retention LOGIN PASSWORD '${RETENTION_DB_PASSWORD}' NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT BYPASSRLS;
    END IF;
  END \$\$;

  -- 创建 schema 并授权
  CREATE SCHEMA IF NOT EXISTS iam AUTHORIZATION flowmesh_iam_migrator;
  CREATE SCHEMA IF NOT EXISTS supplier AUTHORIZATION flowmesh_supplier_migrator;
  CREATE SCHEMA IF NOT EXISTS workflow AUTHORIZATION flowmesh_workflow_migrator;
  CREATE SCHEMA IF NOT EXISTS risk AUTHORIZATION flowmesh_risk_migrator;
  CREATE SCHEMA IF NOT EXISTS audit AUTHORIZATION flowmesh_audit_migrator;

  -- 运行时账号只获得 Schema 使用权；DDL 权限保留给迁移账号。
  GRANT USAGE ON SCHEMA iam TO flowmesh_iam;
  GRANT USAGE ON SCHEMA supplier TO flowmesh_supplier;
  GRANT USAGE ON SCHEMA workflow TO flowmesh_workflow;
  GRANT USAGE ON SCHEMA risk TO flowmesh_risk;
  GRANT USAGE ON SCHEMA audit TO flowmesh_audit;
  GRANT USAGE, CREATE ON SCHEMA iam TO flowmesh_iam_migrator;
  GRANT USAGE, CREATE ON SCHEMA supplier TO flowmesh_supplier_migrator;
  GRANT USAGE, CREATE ON SCHEMA workflow TO flowmesh_workflow_migrator;
  GRANT USAGE, CREATE ON SCHEMA risk TO flowmesh_risk_migrator;
  GRANT USAGE, CREATE ON SCHEMA audit TO flowmesh_audit_migrator;

  -- 未来迁移创建的表和序列默认只向对应业务账号授予运行时所需权限。
  ALTER DEFAULT PRIVILEGES FOR ROLE flowmesh_iam_migrator IN SCHEMA iam
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO flowmesh_iam;
  ALTER DEFAULT PRIVILEGES FOR ROLE flowmesh_iam_migrator IN SCHEMA iam
    GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO flowmesh_iam;
  ALTER DEFAULT PRIVILEGES FOR ROLE flowmesh_supplier_migrator IN SCHEMA supplier
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO flowmesh_supplier;
  ALTER DEFAULT PRIVILEGES FOR ROLE flowmesh_supplier_migrator IN SCHEMA supplier
    GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO flowmesh_supplier;
  ALTER DEFAULT PRIVILEGES FOR ROLE flowmesh_workflow_migrator IN SCHEMA workflow
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO flowmesh_workflow;
  ALTER DEFAULT PRIVILEGES FOR ROLE flowmesh_workflow_migrator IN SCHEMA workflow
    GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO flowmesh_workflow;
  ALTER DEFAULT PRIVILEGES FOR ROLE flowmesh_risk_migrator IN SCHEMA risk
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO flowmesh_risk;
  ALTER DEFAULT PRIVILEGES FOR ROLE flowmesh_risk_migrator IN SCHEMA risk
    GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO flowmesh_risk;
  ALTER DEFAULT PRIVILEGES FOR ROLE flowmesh_audit_migrator IN SCHEMA audit
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO flowmesh_audit;
  ALTER DEFAULT PRIVILEGES FOR ROLE flowmesh_audit_migrator IN SCHEMA audit
    GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO flowmesh_audit;

  -- V3 通知投递迁移会以该角色拥有投递队列表和安全函数。
  GRANT flowmesh_audit_delivery TO flowmesh_audit_migrator;
EOSQL

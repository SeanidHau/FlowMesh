#!/bin/bash
# 作用：初始化 FlowMesh 各服务的数据库角色和 Schema。
# Docker Compose 首次启动 PostgreSQL 时由 docker-entrypoint-initdb.d 自动执行。
# 密码来自 Compose 环境变量（.env）。

set -e

: "${IAM_DB_PASSWORD:?IAM_DB_PASSWORD must be provided}"
: "${SUPPLIER_DB_PASSWORD:?SUPPLIER_DB_PASSWORD must be provided}"
: "${WORKFLOW_DB_PASSWORD:?WORKFLOW_DB_PASSWORD must be provided}"
: "${RISK_DB_PASSWORD:?RISK_DB_PASSWORD must be provided}"
: "${AUDIT_DB_PASSWORD:?AUDIT_DB_PASSWORD must be provided}"
: "${RETENTION_DB_PASSWORD:?RETENTION_DB_PASSWORD must be provided}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
  -- 创建 IAM 服务业务账号（NOSUPERUSER，仅拥有 iam schema）
  DO \$\$
  BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'flowmesh_iam') THEN
      CREATE ROLE flowmesh_iam LOGIN PASSWORD '${IAM_DB_PASSWORD}' NOSUPERUSER;
    END IF;
  END \$\$;

  -- 创建 risk 服务业务账号（NOSUPERUSER，仅拥有 risk schema）
  DO \$\$
  BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'flowmesh_risk') THEN
      CREATE ROLE flowmesh_risk LOGIN PASSWORD '${RISK_DB_PASSWORD}' NOSUPERUSER;
    END IF;
  END \$\$;

  -- 创建通知审计服务业务账号（NOSUPERUSER，仅拥有 audit schema）
  DO \$\$
  BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'flowmesh_audit') THEN
      CREATE ROLE flowmesh_audit LOGIN PASSWORD '${AUDIT_DB_PASSWORD}' NOSUPERUSER;
    END IF;
  END \$\$;

  -- 创建 supplier 服务业务账号（NOSUPERUSER，仅拥有 supplier schema）
  DO \$\$
  BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'flowmesh_supplier') THEN
      CREATE ROLE flowmesh_supplier LOGIN PASSWORD '${SUPPLIER_DB_PASSWORD}' NOSUPERUSER;
    END IF;
  END \$\$;

  -- 创建 workflow 服务业务账号（NOSUPERUSER，仅拥有 workflow schema）
  DO \$\$
  BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'flowmesh_workflow') THEN
      CREATE ROLE flowmesh_workflow LOGIN PASSWORD '${WORKFLOW_DB_PASSWORD}' NOSUPERUSER;
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
  CREATE SCHEMA IF NOT EXISTS iam AUTHORIZATION flowmesh_iam;
  CREATE SCHEMA IF NOT EXISTS supplier AUTHORIZATION flowmesh_supplier;
  CREATE SCHEMA IF NOT EXISTS workflow AUTHORIZATION flowmesh_workflow;
  CREATE SCHEMA IF NOT EXISTS risk AUTHORIZATION flowmesh_risk;
  CREATE SCHEMA IF NOT EXISTS audit AUTHORIZATION flowmesh_audit;

  GRANT ALL ON SCHEMA iam TO flowmesh_iam;
  GRANT ALL ON SCHEMA supplier TO flowmesh_supplier;
  GRANT ALL ON SCHEMA workflow TO flowmesh_workflow;
  GRANT ALL ON SCHEMA risk TO flowmesh_risk;
  GRANT ALL ON SCHEMA audit TO flowmesh_audit;
EOSQL

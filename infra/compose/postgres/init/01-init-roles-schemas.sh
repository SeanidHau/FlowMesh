#!/bin/bash
# 作用：初始化 FlowMesh 各服务的数据库角色和 Schema。
# Docker Compose 首次启动 PostgreSQL 时由 docker-entrypoint-initdb.d 自动执行。
# 密码来自 Compose 环境变量（.env）。

set -e

IAM_DB_PASSWORD="${IAM_DB_PASSWORD:-change-me-iam}"
SUPPLIER_DB_PASSWORD="${SUPPLIER_DB_PASSWORD:-change-me-supplier}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
  -- 创建 IAM 服务业务账号（NOSUPERUSER，仅拥有 iam schema）
  DO \$\$
  BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'flowmesh_iam') THEN
      CREATE ROLE flowmesh_iam LOGIN PASSWORD '${IAM_DB_PASSWORD}' NOSUPERUSER;
    END IF;
  END \$\$;

  -- 创建 supplier 服务业务账号（NOSUPERUSER，仅拥有 supplier schema）
  DO \$\$
  BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'flowmesh_supplier') THEN
      CREATE ROLE flowmesh_supplier LOGIN PASSWORD '${SUPPLIER_DB_PASSWORD}' NOSUPERUSER;
    END IF;
  END \$\$;

  -- 创建 schema 并授权
  CREATE SCHEMA IF NOT EXISTS iam AUTHORIZATION flowmesh_iam;
  CREATE SCHEMA IF NOT EXISTS supplier AUTHORIZATION flowmesh_supplier;

  GRANT ALL ON SCHEMA iam TO flowmesh_iam;
  GRANT ALL ON SCHEMA supplier TO flowmesh_supplier;
EOSQL

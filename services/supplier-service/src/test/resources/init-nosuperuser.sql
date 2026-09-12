-- 创建 flowmesh_supplier NOSUPERUSER 角号（RLS 隔离需要非超级用户）
-- Testcontainers 默认用户 postgres 是超级用户，用其创建 NOSUPERUSER 业务账号。
CREATE ROLE flowmesh_supplier LOGIN PASSWORD 'change-me-supplier' NOSUPERUSER;
CREATE ROLE flowmesh_supplier_migrator LOGIN PASSWORD 'change-me-supplier-migrator'
  NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
CREATE SCHEMA IF NOT EXISTS supplier AUTHORIZATION flowmesh_supplier_migrator;
GRANT USAGE, CREATE ON SCHEMA supplier TO flowmesh_supplier_migrator;
GRANT USAGE ON SCHEMA supplier TO flowmesh_supplier;
ALTER DEFAULT PRIVILEGES FOR ROLE flowmesh_supplier_migrator IN SCHEMA supplier
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO flowmesh_supplier;
ALTER DEFAULT PRIVILEGES FOR ROLE flowmesh_supplier_migrator IN SCHEMA supplier
  GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO flowmesh_supplier;

-- 创建 flowmesh_supplier NOSUPERUSER 角号（RLS 隔离需要非超级用户）
-- Testcontainers 默认用户 postgres 是超级用户，用其创建 NOSUPERUSER 业务账号。
CREATE ROLE flowmesh_supplier LOGIN PASSWORD 'change-me-supplier' NOSUPERUSER;
CREATE SCHEMA IF NOT EXISTS supplier AUTHORIZATION flowmesh_supplier;
GRANT ALL ON SCHEMA supplier TO flowmesh_supplier;

-- 测试容器使用 postgres 管理账号初始化独立的迁移账号和运行时账号。
CREATE ROLE flowmesh_iam LOGIN PASSWORD 'change-me-iam' NOSUPERUSER;
CREATE ROLE flowmesh_iam_migrator LOGIN PASSWORD 'change-me-iam-migrator'
  NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
CREATE SCHEMA iam AUTHORIZATION flowmesh_iam_migrator;
GRANT USAGE, CREATE ON SCHEMA iam TO flowmesh_iam_migrator;
GRANT USAGE ON SCHEMA iam TO flowmesh_iam;
ALTER DEFAULT PRIVILEGES FOR ROLE flowmesh_iam_migrator IN SCHEMA iam
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO flowmesh_iam;
ALTER DEFAULT PRIVILEGES FOR ROLE flowmesh_iam_migrator IN SCHEMA iam
  GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO flowmesh_iam;

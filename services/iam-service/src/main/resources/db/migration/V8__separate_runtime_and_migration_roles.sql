-- V8：将 IAM 的 Flyway 迁移账号与运行时业务账号分离。
-- 迁移账号负责 DDL；业务账号只获得应用运行所需的表级权限。
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'flowmesh_iam_migrator') THEN
        RAISE EXCEPTION 'required database role flowmesh_iam_migrator is not provisioned';
    END IF;
END
$$;

GRANT USAGE ON SCHEMA iam TO flowmesh_iam;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA iam TO flowmesh_iam;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA iam TO flowmesh_iam;

ALTER DEFAULT PRIVILEGES FOR ROLE flowmesh_iam_migrator IN SCHEMA iam
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO flowmesh_iam;
ALTER DEFAULT PRIVILEGES FOR ROLE flowmesh_iam_migrator IN SCHEMA iam
    GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO flowmesh_iam;

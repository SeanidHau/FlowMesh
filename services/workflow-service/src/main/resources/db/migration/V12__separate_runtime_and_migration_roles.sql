-- V12：将 workflow 的 Flyway 迁移账号与运行时业务账号分离。
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'flowmesh_workflow_migrator') THEN
        RAISE EXCEPTION 'required database role flowmesh_workflow_migrator is not provisioned';
    END IF;
END
$$;

GRANT USAGE ON SCHEMA workflow TO flowmesh_workflow;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA workflow TO flowmesh_workflow;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA workflow TO flowmesh_workflow;

ALTER DEFAULT PRIVILEGES FOR ROLE flowmesh_workflow_migrator IN SCHEMA workflow
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO flowmesh_workflow;
ALTER DEFAULT PRIVILEGES FOR ROLE flowmesh_workflow_migrator IN SCHEMA workflow
    GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO flowmesh_workflow;

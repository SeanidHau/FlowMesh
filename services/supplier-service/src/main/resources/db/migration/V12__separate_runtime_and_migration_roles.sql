-- V12：将 supplier 的 Flyway 迁移账号与运行时业务账号分离。
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'flowmesh_supplier_migrator') THEN
        RAISE EXCEPTION 'required database role flowmesh_supplier_migrator is not provisioned';
    END IF;
END
$$;

GRANT USAGE ON SCHEMA supplier TO flowmesh_supplier;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA supplier TO flowmesh_supplier;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA supplier TO flowmesh_supplier;

ALTER DEFAULT PRIVILEGES FOR ROLE flowmesh_supplier_migrator IN SCHEMA supplier
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO flowmesh_supplier;
ALTER DEFAULT PRIVILEGES FOR ROLE flowmesh_supplier_migrator IN SCHEMA supplier
    GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO flowmesh_supplier;

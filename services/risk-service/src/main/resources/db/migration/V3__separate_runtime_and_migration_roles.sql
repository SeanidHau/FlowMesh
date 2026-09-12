-- V3：将 risk 的 Flyway 迁移账号与运行时业务账号分离。
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'flowmesh_risk_migrator') THEN
        RAISE EXCEPTION 'required database role flowmesh_risk_migrator is not provisioned';
    END IF;
END
$$;

GRANT USAGE ON SCHEMA risk TO flowmesh_risk;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA risk TO flowmesh_risk;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA risk TO flowmesh_risk;

ALTER DEFAULT PRIVILEGES FOR ROLE flowmesh_risk_migrator IN SCHEMA risk
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO flowmesh_risk;
ALTER DEFAULT PRIVILEGES FOR ROLE flowmesh_risk_migrator IN SCHEMA risk
    GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO flowmesh_risk;

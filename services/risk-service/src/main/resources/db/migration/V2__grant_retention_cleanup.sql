-- V2：为专用生命周期维护角色授予 risk Schema 内 Outbox 清理权限。
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'flowmesh_retention') THEN
        EXECUTE 'GRANT USAGE ON SCHEMA risk TO flowmesh_retention';
        EXECUTE 'GRANT SELECT, DELETE ON risk.risk_outbox_events TO flowmesh_retention';
        EXECUTE 'GRANT UPDATE (id) ON risk.risk_outbox_events TO flowmesh_retention';
    END IF;
END $$;

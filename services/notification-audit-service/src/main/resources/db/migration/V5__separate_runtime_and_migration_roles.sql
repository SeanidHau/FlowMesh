-- V5：将 audit 的 Flyway 迁移账号与运行时业务账号分离。
-- notification_deliveries 仍由 flowmesh_audit_delivery 拥有，业务账号只能通过
-- V3 授予的 INSERT 和安全函数访问它，不能因本次通用授权重新获得跨租户读取权限。
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'flowmesh_audit_migrator') THEN
        RAISE EXCEPTION 'required database role flowmesh_audit_migrator is not provisioned';
    END IF;
END
$$;

GRANT USAGE ON SCHEMA audit TO flowmesh_audit;
GRANT SELECT, INSERT, UPDATE, DELETE
    ON audit.audit_event_inbox, audit.audit_events, audit.notifications
    TO flowmesh_audit;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA audit TO flowmesh_audit;

ALTER DEFAULT PRIVILEGES FOR ROLE flowmesh_audit_migrator IN SCHEMA audit
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO flowmesh_audit;
ALTER DEFAULT PRIVILEGES FOR ROLE flowmesh_audit_migrator IN SCHEMA audit
    GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO flowmesh_audit;

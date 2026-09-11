-- V2：为专用生命周期维护角色授予 audit Schema 内 Inbox 清理权限。
-- audit_events 和 notifications 按设计保留，不授予生命周期任务删除权限。
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'flowmesh_retention') THEN
        EXECUTE 'GRANT USAGE ON SCHEMA audit TO flowmesh_retention';
        EXECUTE 'GRANT SELECT, DELETE ON audit.audit_event_inbox TO flowmesh_retention';
        EXECUTE 'GRANT UPDATE (event_id) ON audit.audit_event_inbox TO flowmesh_retention';
    END IF;
END $$;

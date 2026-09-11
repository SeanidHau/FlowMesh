-- V4：为投递队列授予专用生命周期维护账号的最小清理权限。
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'flowmesh_retention') THEN
        EXECUTE 'GRANT SELECT, DELETE ON audit.notification_deliveries TO flowmesh_retention';
        EXECUTE 'GRANT UPDATE (id) ON audit.notification_deliveries TO flowmesh_retention';
    END IF;
END $$;

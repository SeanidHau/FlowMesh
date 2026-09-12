-- V4：为投递队列授予专用生命周期维护账号的最小清理权限。
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'flowmesh_audit_delivery') THEN
        RAISE EXCEPTION 'required database role flowmesh_audit_delivery is not provisioned';
    END IF;
END $$;

-- notification_deliveries 的所有权在 V3 已转给 BYPASSRLS 角色；只有表所有者
-- 能向生命周期账号授予清理权限，因此迁移期间显式切换角色。
SET ROLE flowmesh_audit_delivery;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'flowmesh_retention') THEN
        EXECUTE 'GRANT SELECT, DELETE ON audit.notification_deliveries TO flowmesh_retention';
        EXECUTE 'GRANT UPDATE (id) ON audit.notification_deliveries TO flowmesh_retention';
    END IF;
END $$;
RESET ROLE;

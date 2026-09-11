-- V8：为专用生命周期维护角色授予 supplier Schema 内清理表的最小权限。
-- 角色由部署平台预先创建；本迁移在本地角色不存在时保持兼容，但生产预检必须拒绝缺失角色。
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'flowmesh_retention') THEN
        EXECUTE 'GRANT USAGE ON SCHEMA supplier TO flowmesh_retention';
        EXECUTE 'GRANT SELECT, DELETE ON supplier.supplier_outbox_events, supplier.supplier_outbox_replay_audits, supplier.supplier_workflow_event_inbox, supplier.supplier_idempotency_keys TO flowmesh_retention';
        EXECUTE 'GRANT UPDATE (id) ON supplier.supplier_outbox_events, supplier.supplier_outbox_replay_audits, supplier.supplier_idempotency_keys TO flowmesh_retention';
        EXECUTE 'GRANT UPDATE (event_id) ON supplier.supplier_workflow_event_inbox TO flowmesh_retention';
    END IF;
END $$;

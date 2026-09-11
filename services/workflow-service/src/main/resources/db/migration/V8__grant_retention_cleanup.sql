-- V8：为专用生命周期维护角色授予 workflow Schema 内清理表的最小权限。
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'flowmesh_retention') THEN
        EXECUTE 'GRANT USAGE ON SCHEMA workflow TO flowmesh_retention';
        EXECUTE 'GRANT SELECT, DELETE ON workflow.workflow_outbox_events, workflow.workflow_outbox_replay_audits, workflow.workflow_risk_event_inbox TO flowmesh_retention';
        EXECUTE 'GRANT UPDATE (id) ON workflow.workflow_outbox_events, workflow.workflow_outbox_replay_audits TO flowmesh_retention';
        EXECUTE 'GRANT UPDATE (event_id) ON workflow.workflow_risk_event_inbox TO flowmesh_retention';
    END IF;
END $$;

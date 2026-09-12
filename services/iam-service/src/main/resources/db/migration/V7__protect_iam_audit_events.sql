-- V7: 将 IAM 安全审计记录固化为数据库层 append-only 数据。
-- 即使应用层误调用 UPDATE/DELETE，也不能篡改或删除既有安全证据。

CREATE OR REPLACE FUNCTION prevent_iam_audit_event_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'iam_audit_events is append-only; operation % is not allowed', TG_OP;
END;
$$;

CREATE TRIGGER trg_iam_audit_events_no_update_delete
    BEFORE UPDATE OR DELETE ON iam_audit_events
    FOR EACH ROW
    EXECUTE FUNCTION prevent_iam_audit_event_mutation();

CREATE TRIGGER trg_iam_audit_events_no_truncate
    BEFORE TRUNCATE ON iam_audit_events
    FOR EACH STATEMENT
    EXECUTE FUNCTION prevent_iam_audit_event_mutation();

-- 降低误授权风险；表所有者仍由触发器保护，不能绕过审计不可变性。
REVOKE UPDATE, DELETE, TRUNCATE ON iam_audit_events FROM PUBLIC;

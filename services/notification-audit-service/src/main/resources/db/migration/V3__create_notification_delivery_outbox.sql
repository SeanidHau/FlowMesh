-- V3：为外部通知投递建立可靠的事务性队列表。
-- 该表只由通知审计服务内部使用，不通过 HTTP API 暴露；业务写入仍在租户事务和 RLS 上下文内完成。
CREATE TABLE notification_deliveries (
    id UUID PRIMARY KEY,
    notification_id UUID NOT NULL UNIQUE REFERENCES notifications (id),
    source_event_id UUID NOT NULL UNIQUE,
    tenant_id VARCHAR(64) NOT NULL,
    recipient_user_id UUID NOT NULL,
    notification_type VARCHAR(128) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content VARCHAR(1000) NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN ('PENDING', 'DELIVERED', 'DEAD_LETTER')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    claimed_until TIMESTAMP WITH TIME ZONE,
    claim_token UUID,
    delivered_at TIMESTAMP WITH TIME ZONE,
    last_error VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_notification_deliveries_pending
    ON notification_deliveries (next_attempt_at, created_at)
    WHERE status = 'PENDING';
CREATE INDEX idx_notification_deliveries_retention
    ON notification_deliveries (status, updated_at);

ALTER TABLE notification_deliveries ENABLE ROW LEVEL SECURITY;
ALTER TABLE notification_deliveries FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_notification_deliveries ON notification_deliveries
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::text)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::text);

-- 发布器需要跨租户扫描，但业务账号不应直接读取队列明细。
-- 该角色由数据库管理员或初始化脚本预置，并且必须赋予 flowmesh_audit SET ROLE 能力。
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'flowmesh_audit_delivery') THEN
        RAISE EXCEPTION 'required database role flowmesh_audit_delivery is not provisioned';
    END IF;
    IF EXISTS (
        SELECT 1 FROM pg_roles
         WHERE rolname = current_user AND rolinherit
    ) THEN
        RAISE EXCEPTION 'database role % must be NOINHERIT before notification delivery migration', current_user;
    END IF;
END
$$;

-- 临时切换到专用 BYPASSRLS 角色完成表所有权和函数所有权转移。
-- 运行时业务账号只获得 INSERT 与函数 EXECUTE，不直接获得跨租户 SELECT/UPDATE。
GRANT USAGE, CREATE ON SCHEMA audit TO flowmesh_audit_delivery;
SET ROLE flowmesh_audit_delivery;
ALTER TABLE notification_deliveries OWNER TO flowmesh_audit_delivery;

CREATE OR REPLACE FUNCTION audit.claim_notification_deliveries(
    p_now TIMESTAMP WITH TIME ZONE,
    p_claim_token UUID,
    p_claimed_until TIMESTAMP WITH TIME ZONE,
    p_limit INTEGER
)
RETURNS TABLE (
    id UUID,
    notification_id UUID,
    source_event_id UUID,
    tenant_id VARCHAR(64),
    recipient_user_id UUID,
    notification_type VARCHAR(128),
    title VARCHAR(255),
    content VARCHAR(1000),
    status VARCHAR(32),
    attempts INTEGER,
    next_attempt_at TIMESTAMP WITH TIME ZONE,
    claimed_until TIMESTAMP WITH TIME ZONE,
    claim_token UUID,
    created_at TIMESTAMP WITH TIME ZONE
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = audit, pg_temp
AS $$
BEGIN
    RETURN QUERY
    WITH candidates AS (
        SELECT d.id
          FROM notification_deliveries d
         WHERE d.status = 'PENDING'
           AND d.next_attempt_at <= p_now
           AND (d.claimed_until IS NULL OR d.claimed_until < p_now)
         ORDER BY d.next_attempt_at, d.created_at, d.id
         FOR UPDATE SKIP LOCKED
         LIMIT p_limit
    )
    UPDATE notification_deliveries target
       SET claimed_until = p_claimed_until,
           claim_token = p_claim_token,
           updated_at = now()
      FROM candidates
     WHERE target.id = candidates.id
    RETURNING target.id, target.notification_id, target.source_event_id, target.tenant_id,
              target.recipient_user_id, target.notification_type, target.title, target.content,
              target.status, target.attempts, target.next_attempt_at, target.claimed_until,
              target.claim_token, target.created_at;
END
$$;

CREATE OR REPLACE FUNCTION audit.mark_notification_delivery_delivered(
    p_id UUID,
    p_claim_token UUID,
    p_delivered_at TIMESTAMP WITH TIME ZONE
)
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = audit, pg_temp
AS $$
DECLARE
    affected INTEGER;
BEGIN
    UPDATE notification_deliveries
       SET status = 'DELIVERED', delivered_at = p_delivered_at,
           claimed_until = NULL, claim_token = NULL, updated_at = now()
     WHERE id = p_id AND status = 'PENDING' AND claim_token = p_claim_token;
    GET DIAGNOSTICS affected = ROW_COUNT;
    RETURN affected;
END
$$;

CREATE OR REPLACE FUNCTION audit.mark_notification_delivery_failed(
    p_id UUID,
    p_claim_token UUID,
    p_attempts INTEGER,
    p_next_attempt_at TIMESTAMP WITH TIME ZONE,
    p_status VARCHAR(32),
    p_last_error VARCHAR(500)
)
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = audit, pg_temp
AS $$
DECLARE
    affected INTEGER;
BEGIN
    UPDATE notification_deliveries
       SET status = p_status, attempts = p_attempts, next_attempt_at = p_next_attempt_at,
           last_error = p_last_error, claimed_until = NULL, claim_token = NULL, updated_at = now()
     WHERE id = p_id AND status = 'PENDING' AND claim_token = p_claim_token;
    GET DIAGNOSTICS affected = ROW_COUNT;
    RETURN affected;
END
$$;

CREATE OR REPLACE FUNCTION audit.count_pending_notification_deliveries()
RETURNS BIGINT
LANGUAGE sql
SECURITY DEFINER
SET search_path = audit, pg_temp
AS $$ SELECT COUNT(*) FROM notification_deliveries WHERE status = 'PENDING' $$;

CREATE OR REPLACE FUNCTION audit.count_dead_lettered_notification_deliveries()
RETURNS BIGINT
LANGUAGE sql
SECURITY DEFINER
SET search_path = audit, pg_temp
AS $$ SELECT COUNT(*) FROM notification_deliveries WHERE status = 'DEAD_LETTER' $$;

REVOKE ALL ON notification_deliveries FROM flowmesh_audit;
GRANT INSERT ON notification_deliveries TO flowmesh_audit;
GRANT EXECUTE ON FUNCTION audit.claim_notification_deliveries(TIMESTAMP WITH TIME ZONE, UUID, TIMESTAMP WITH TIME ZONE, INTEGER) TO flowmesh_audit;
GRANT EXECUTE ON FUNCTION audit.mark_notification_delivery_delivered(UUID, UUID, TIMESTAMP WITH TIME ZONE) TO flowmesh_audit;
GRANT EXECUTE ON FUNCTION audit.mark_notification_delivery_failed(UUID, UUID, INTEGER, TIMESTAMP WITH TIME ZONE, VARCHAR, VARCHAR) TO flowmesh_audit;
GRANT EXECUTE ON FUNCTION audit.count_pending_notification_deliveries() TO flowmesh_audit;
GRANT EXECUTE ON FUNCTION audit.count_dead_lettered_notification_deliveries() TO flowmesh_audit;
RESET ROLE;
REVOKE CREATE ON SCHEMA audit FROM flowmesh_audit_delivery;

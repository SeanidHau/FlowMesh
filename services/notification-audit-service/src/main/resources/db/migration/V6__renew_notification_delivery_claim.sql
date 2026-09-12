-- V6：为外部通知投递增加发送前续租，降低批次发送较慢时的重复投递窗口。

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'flowmesh_audit_delivery') THEN
        RAISE EXCEPTION 'required database role flowmesh_audit_delivery is not provisioned';
    END IF;
END
$$;

-- 迁移账号拥有 audit Schema，但续租函数必须归属于专用投递角色。
-- 仅在创建函数期间临时补齐角色权限，迁移结束后立即撤销 CREATE。
GRANT USAGE, CREATE ON SCHEMA audit TO flowmesh_audit_delivery;
SET ROLE flowmesh_audit_delivery;

CREATE OR REPLACE FUNCTION audit.renew_notification_delivery(
    p_id UUID,
    p_claim_token UUID,
    p_claimed_until TIMESTAMP WITH TIME ZONE
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
       SET claimed_until = p_claimed_until, updated_at = now()
     WHERE id = p_id
       AND status = 'PENDING'
       AND claim_token = p_claim_token
       AND claimed_until > now();
    GET DIAGNOSTICS affected = ROW_COUNT;
    RETURN affected;
END
$$;

GRANT EXECUTE ON FUNCTION audit.renew_notification_delivery(UUID, UUID, TIMESTAMP WITH TIME ZONE)
    TO flowmesh_audit;

RESET ROLE;
REVOKE CREATE ON SCHEMA audit FROM flowmesh_audit_delivery;

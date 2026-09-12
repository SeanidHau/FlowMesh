-- V6：为外部通知投递增加发送前续租，降低批次发送较慢时的重复投递窗口。

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'flowmesh_audit_delivery') THEN
        RAISE EXCEPTION 'required database role flowmesh_audit_delivery is not provisioned';
    END IF;
END
$$;

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

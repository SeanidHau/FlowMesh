-- V11：为历史租户策略显式补充 WITH CHECK，禁止跨租户插入或修改。
DROP POLICY tenant_isolation_applications ON supplier_applications;
CREATE POLICY tenant_isolation_applications ON supplier_applications
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::text)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::text);

DROP POLICY tenant_isolation_idempotency ON supplier_idempotency_keys;
CREATE POLICY tenant_isolation_idempotency ON supplier_idempotency_keys
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::text)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::text);

DROP POLICY tenant_isolation_supplier_workflow_event_inbox ON supplier_workflow_event_inbox;
CREATE POLICY tenant_isolation_supplier_workflow_event_inbox
    ON supplier_workflow_event_inbox
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::text)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::text);

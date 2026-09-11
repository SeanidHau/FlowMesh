-- V11：为 workflow 实例租户策略显式补充 WITH CHECK，禁止跨租户写入。
DROP POLICY tenant_isolation_workflow_instances ON workflow_instances;
CREATE POLICY tenant_isolation_workflow_instances ON workflow_instances
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::text)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::text);

-- V9: 为 IAM 的租户数据启用强制 RLS，并为 Refresh Token 固化租户归属。

-- Refresh Token 原表只有 user_id；冗余保存 tenant_id，使认证前的令牌查询也能使用 RLS。
ALTER TABLE iam_audit_events
    ADD CONSTRAINT fk_iam_audit_events_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id);

ALTER TABLE iam_users
    ADD CONSTRAINT uk_iam_users_id_tenant UNIQUE (id, tenant_id);

ALTER TABLE iam_refresh_tokens
    ADD COLUMN tenant_id VARCHAR(64);

UPDATE iam_refresh_tokens r
SET tenant_id = u.tenant_id
FROM iam_users u
WHERE u.id = r.user_id;

ALTER TABLE iam_refresh_tokens
    ALTER COLUMN tenant_id SET NOT NULL;

ALTER TABLE iam_refresh_tokens
    ADD CONSTRAINT fk_iam_refresh_tokens_user_tenant
        FOREIGN KEY (user_id, tenant_id) REFERENCES iam_users (id, tenant_id);

CREATE INDEX idx_iam_refresh_tokens_tenant
    ON iam_refresh_tokens (tenant_id, created_at);

-- 用户、角色关系、刷新令牌和认证审计均只能访问当前事务租户的数据。
ALTER TABLE iam_users ENABLE ROW LEVEL SECURITY;
ALTER TABLE iam_users FORCE ROW LEVEL SECURITY;
ALTER TABLE iam_user_roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE iam_user_roles FORCE ROW LEVEL SECURITY;
ALTER TABLE iam_refresh_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE iam_refresh_tokens FORCE ROW LEVEL SECURITY;
ALTER TABLE iam_audit_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE iam_audit_events FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_iam_users ON iam_users
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::text)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::text);

CREATE POLICY tenant_isolation_iam_user_roles ON iam_user_roles
    USING (EXISTS (
        SELECT 1
        FROM iam_users u
        WHERE u.id = iam_user_roles.user_id
          AND u.tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::text
    ))
    WITH CHECK (EXISTS (
        SELECT 1
        FROM iam_users u
        WHERE u.id = iam_user_roles.user_id
          AND u.tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::text
    ));

CREATE POLICY tenant_isolation_iam_refresh_tokens ON iam_refresh_tokens
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::text)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::text);

CREATE POLICY tenant_isolation_iam_audit_events ON iam_audit_events
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::text)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::text);

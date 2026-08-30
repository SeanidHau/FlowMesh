-- V1: supplier 服务 schema —— 供应商申请表与幂等记录表，启用 RLS。

-- 供应商申请表
CREATE TABLE supplier_applications (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    applicant_user_id UUID NOT NULL,
    supplier_name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    state_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- 幂等记录表
CREATE TABLE supplier_idempotency_keys (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    user_id UUID NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    response_status INT NOT NULL,
    response_body JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_supplier_idempotency UNIQUE (tenant_id, user_id, idempotency_key)
);

-- 索引
CREATE INDEX idx_supplier_applications_tenant ON supplier_applications (tenant_id);
CREATE INDEX idx_supplier_idempotency_tenant ON supplier_idempotency_keys (tenant_id);

-- 启用 RLS 并 FORCE（owner 也受约束）
ALTER TABLE supplier_applications ENABLE ROW LEVEL SECURITY;
ALTER TABLE supplier_applications FORCE ROW LEVEL SECURITY;
ALTER TABLE supplier_idempotency_keys ENABLE ROW LEVEL SECURITY;
ALTER TABLE supplier_idempotency_keys FORCE ROW LEVEL SECURITY;

-- RLS 策略：仅可见当前租户数据
CREATE POLICY tenant_isolation_applications ON supplier_applications
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::text);

CREATE POLICY tenant_isolation_idempotency ON supplier_idempotency_keys
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::text);

-- V7: 供应商材料元数据。文件内容存储在 MinIO，数据库只保存受 RLS 保护的元数据。

CREATE TABLE supplier_documents (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    application_id UUID NOT NULL REFERENCES supplier_applications(id),
    uploaded_by UUID NOT NULL,
    object_key VARCHAR(512) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
    sha256 CHAR(64) NOT NULL,
    scan_status VARCHAR(16) NOT NULL CHECK (scan_status IN ('CLEAN', 'SKIPPED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_supplier_documents_object_key UNIQUE (object_key)
);

CREATE INDEX idx_supplier_documents_application
    ON supplier_documents (tenant_id, application_id, created_at);

ALTER TABLE supplier_documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE supplier_documents FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_documents ON supplier_documents
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::text)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::text);

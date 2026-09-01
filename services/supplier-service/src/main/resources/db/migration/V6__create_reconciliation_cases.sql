-- V6: 保存跨服务状态对账发现的待处置记录。
CREATE TABLE supplier_reconciliation_cases (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    application_id UUID NOT NULL,
    discrepancy_type VARCHAR(64) NOT NULL,
    details JSONB NOT NULL,
    status VARCHAR(16) NOT NULL,
    detected_at TIMESTAMP WITH TIME ZONE NOT NULL,
    resolved_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_supplier_reconciliation_open UNIQUE (tenant_id, application_id, discrepancy_type)
);

CREATE INDEX idx_supplier_reconciliation_pending
    ON supplier_reconciliation_cases (tenant_id, status, detected_at DESC);

-- V9：保存补件轮次历史，并允许申请人在补件状态下重新提交。
ALTER TABLE supplier_applications
    DROP CONSTRAINT ck_supplier_applications_status,
    ADD COLUMN supplement_count INT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_supplier_applications_status
        CHECK (status IN ('SUBMITTED', 'IN_REVIEW', 'SUPPLEMENT_REQUIRED', 'ENABLED')),
    ADD CONSTRAINT ck_supplier_applications_supplement_count
        CHECK (supplement_count BETWEEN 0 AND 2);

CREATE TABLE supplier_application_supplements (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    application_id UUID NOT NULL REFERENCES supplier_applications(id),
    round_no INT NOT NULL,
    submitted_by UUID NOT NULL,
    comment VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX uk_supplier_supplement_round
    ON supplier_application_supplements (application_id, round_no);

CREATE INDEX idx_supplier_supplement_tenant_application
    ON supplier_application_supplements (tenant_id, application_id, created_at);

ALTER TABLE supplier_application_supplements ENABLE ROW LEVEL SECURITY;
ALTER TABLE supplier_application_supplements FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_supplier_supplements
    ON supplier_application_supplements
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::text)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::text);

-- V10：允许供应商申请记录风控拒绝终态。
ALTER TABLE supplier_applications
    DROP CONSTRAINT IF EXISTS ck_supplier_applications_status;

ALTER TABLE supplier_applications
    ADD CONSTRAINT ck_supplier_applications_status
        CHECK (status IN ('SUBMITTED', 'IN_REVIEW', 'SUPPLEMENT_REQUIRED', 'REJECTED', 'ENABLED'));

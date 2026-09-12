package com.flowmesh.supplier.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

/**
 * 验证供应商申请请求与数据库字段长度保持一致。
 */
class CreateApplicationRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    /** 验证过长供应商名称在持久化前被拒绝。 */
    @Test
    void shouldRejectOversizedSupplierName() {
        var violations = validator.validate(new CreateApplicationRequest("x".repeat(256)));

        assertThat(violations).anyMatch(violation ->
            violation.getPropertyPath().toString().equals("supplierName"));
    }
}

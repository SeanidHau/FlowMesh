package com.flowmesh.workflow.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

/**
 * 验证审批任务请求的输入长度边界。
 */
class CompleteTaskRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    /** 验证过长任务键在枚举解析前被拒绝。 */
    @Test
    void shouldRejectOversizedTaskKey() {
        var violations = validator.validate(new CompleteTaskRequest(
            "x".repeat(65), "APPROVE", null
        ));

        assertThat(violations).anyMatch(violation ->
            violation.getPropertyPath().toString().equals("taskKey"));
    }
}

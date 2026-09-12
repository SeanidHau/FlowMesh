package com.flowmesh.iam.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

/**
 * 验证认证请求在进入业务层前执行长度边界校验。
 */
class AuthRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    /** 验证过长密码不会进入 BCrypt 校验流程。 */
    @Test
    void shouldRejectOversizedPassword() {
        var violations = validator.validate(new LoginRequest(
            "tenant-a", "applicant-a", "x".repeat(129)
        ));

        assertThat(violations).anyMatch(violation ->
            violation.getPropertyPath().toString().equals("password"));
    }

    /** 验证过长 Refresh Token 不会进入哈希和数据库查询流程。 */
    @Test
    void shouldRejectOversizedRefreshToken() {
        var violations = validator.validate(new RefreshRequest(
            "tenant-a", "x".repeat(129)
        ));

        assertThat(violations).anyMatch(violation ->
            violation.getPropertyPath().toString().equals("refreshToken"));
    }
}

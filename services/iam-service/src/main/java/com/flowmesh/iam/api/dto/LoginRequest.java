package com.flowmesh.iam.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求。
 *
 * @param tenantId 租户标识
 * @param username 用户名
 * @param password 密码明文
 */
public record LoginRequest(
    @NotBlank String tenantId,
    @NotBlank String username,
    @NotBlank String password
) {
}

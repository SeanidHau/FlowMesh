package com.flowmesh.iam.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登录请求。
 *
 * @param tenantId 租户标识
 * @param username 用户名
 * @param password 密码明文
 */
public record LoginRequest(
    @NotBlank @Size(max = 64) String tenantId,
    @NotBlank @Size(max = 64) String username,
    @NotBlank @Size(max = 128) String password
) {
}

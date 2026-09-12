package com.flowmesh.iam.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登出请求。
 *
 * @param tenantId 租户标识，用于在解析不透明令牌前建立 RLS 上下文
 * @param refreshToken 待撤销的刷新令牌
 */
public record LogoutRequest(
    @NotBlank @Size(max = 64) String tenantId,
    @NotBlank @Size(max = 128) String refreshToken
) {
}

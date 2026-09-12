package com.flowmesh.iam.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 刷新令牌请求。
 *
 * @param tenantId 租户标识，用于在解析不透明令牌前建立 RLS 上下文
 * @param refreshToken 原始刷新令牌
 */
public record RefreshRequest(
    @NotBlank @Size(max = 64) String tenantId,
    @NotBlank @Size(max = 128) String refreshToken
) {
}

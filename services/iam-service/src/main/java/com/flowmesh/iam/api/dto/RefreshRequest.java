package com.flowmesh.iam.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 刷新令牌请求。
 *
 * @param refreshToken 原始刷新令牌
 */
public record RefreshRequest(
    @NotBlank String refreshToken
) {
}

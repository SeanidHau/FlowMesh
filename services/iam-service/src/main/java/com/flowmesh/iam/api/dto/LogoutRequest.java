package com.flowmesh.iam.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登出请求。
 *
 * @param refreshToken 待撤销的刷新令牌
 */
public record LogoutRequest(
    @NotBlank String refreshToken
) {
}

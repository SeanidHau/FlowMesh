package com.flowmesh.iam.api.dto;

/**
 * 认证成功后返回的令牌对。
 *
 * @param accessToken 短期 Access Token
 * @param refreshToken 长期 Refresh Token
 */
public record TokenResponse(
    String accessToken,
    String refreshToken
) {
}

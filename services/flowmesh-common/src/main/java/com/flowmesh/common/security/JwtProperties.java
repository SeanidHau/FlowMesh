package com.flowmesh.common.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 承载 JWT 签发和校验所需的安全配置。
 *
 * <p>该配置由 iam-service 和 supplier-service 共用。签名密钥通过环境变量注入，
 * 且必须是至少 32 字节随机数据的 Base64 编码值。</p>
 */
@Validated
@ConfigurationProperties(prefix = "flowmesh.security.jwt")
public class JwtProperties {

    /** JWT 签发方标识。 */
    @NotBlank
    private String issuer;

    /** Base64 编码的 HMAC 签名密钥。 */
    @NotBlank
    private String signingKey;

    /** Access Token 有效期。 */
    @NotNull
    private Duration accessTokenTtl;

    /** Refresh Token 有效期。 */
    @NotNull
    private Duration refreshTokenTtl;

    /**
     * 获取 JWT 签发方标识。
     *
     * @return 签发方标识
     */
    public String getIssuer() {
        return issuer;
    }

    /**
     * 设置 JWT 签发方标识。
     *
     * @param issuer 签发方标识
     */
    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    /**
     * 获取 Base64 编码的 HMAC 签名密钥。
     *
     * @return 签名密钥
     */
    public String getSigningKey() {
        return signingKey;
    }

    /**
     * 设置 Base64 编码的 HMAC 签名密钥。
     *
     * @param signingKey 签名密钥
     */
    public void setSigningKey(String signingKey) {
        this.signingKey = signingKey;
    }

    /**
     * 获取 Access Token 有效期。
     *
     * @return Access Token 有效期
     */
    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    /**
     * 设置 Access Token 有效期。
     *
     * @param accessTokenTtl Access Token 有效期
     */
    public void setAccessTokenTtl(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }

    /**
     * 获取 Refresh Token 有效期。
     *
     * @return Refresh Token 有效期
     */
    public Duration getRefreshTokenTtl() {
        return refreshTokenTtl;
    }

    /**
     * 设置 Refresh Token 有效期。
     *
     * @param refreshTokenTtl Refresh Token 有效期
     */
    public void setRefreshTokenTtl(Duration refreshTokenTtl) {
        this.refreshTokenTtl = refreshTokenTtl;
    }
}

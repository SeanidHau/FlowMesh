package com.flowmesh.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 签发并校验 Access Token。
 *
 * <p>JWT 仅携带已认证用户的标识、用户名、可信租户和角色声明。Refresh Token 不使用该类签发，
 * 而是作为随机值以哈希形式持久化。</p>
 */
@Service
public class JwtService {

    /** JWT 用户名声明名称。 */
    public static final String USERNAME_CLAIM = "username";

    /** JWT 租户标识声明名称。 */
    public static final String TENANT_ID_CLAIM = "tenantId";

    /** JWT 角色集合声明名称。 */
    public static final String ROLES_CLAIM = "roles";

    private final JwtProperties jwtProperties;
    private final SecretKey signingKey;

    /**
     * 根据受校验的配置创建 JWT 服务。
     *
     * @param jwtProperties JWT 签发方、密钥和有效期配置
     */
    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.signingKey = createSigningKey(jwtProperties.getSigningKey());
    }

    /**
     * 为已认证主体签发 Access Token。
     *
     * @param principal 已认证且已完成授权加载的请求主体
     * @param issuedAt 令牌签发时间
     * @return 紧凑序列化的已签名 JWT
     */
    public String issueAccessToken(AuthPrincipal principal, Instant issuedAt) {
        Objects.requireNonNull(principal);
        Objects.requireNonNull(issuedAt);

        return Jwts.builder()
            .issuer(jwtProperties.getIssuer())
            .subject(principal.userId().toString())
            .claim(USERNAME_CLAIM, principal.username())
            .claim(TENANT_ID_CLAIM, principal.tenantId())
            .claim(ROLES_CLAIM, principal.roles().stream().sorted().toList())
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(issuedAt.plus(jwtProperties.getAccessTokenTtl())))
            .signWith(signingKey)
            .compact();
    }

    /**
     * 校验 Access Token 签名、签发方和有效期，并还原可信请求主体。
     *
     * <p>解析失败（签名错误、过期、缺 claim 等）时抛出异常，
     * 由 {@link JwtAuthenticationFilter} 捕获并统一转为 401。</p>
     *
     * @param token 客户端提交的紧凑序列化 JWT
     * @return 经签名验证后的请求主体
     */
    public AuthPrincipal parseAccessToken(String token) {
        Claims claims = Jwts.parser()
            .verifyWith(signingKey)
            .requireIssuer(jwtProperties.getIssuer())
            .build()
            .parseSignedClaims(token)
            .getPayload();

        return new AuthPrincipal(
            UUID.fromString(Objects.requireNonNull(claims.getSubject())),
            requiredStringClaim(claims, USERNAME_CLAIM),
            requiredStringClaim(claims, TENANT_ID_CLAIM),
            extractRoles(claims)
        );
    }

    private static SecretKey createSigningKey(String signingKey) {
        try {
            return Keys.hmacShaKeyFor(Decoders.BASE64.decode(signingKey));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                "JWT_SIGNING_KEY 必须是至少 32 字节随机数据的 Base64 编码值。",
                exception
            );
        }
    }

    private static String requiredStringClaim(Claims claims, String claimName) {
        return Objects.requireNonNull(
            claims.get(claimName, String.class),
            "JWT 缺少必要声明：" + claimName
        );
    }

    private static Set<String> extractRoles(Claims claims) {
        List<?> roles = claims.get(ROLES_CLAIM, List.class);
        if (roles == null) {
            return Set.of();
        }

        return roles.stream()
            .map(String.class::cast)
            .collect(Collectors.toUnmodifiableSet());
    }
}

package com.flowmesh.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 验证 Access Token 的签发与校验行为。
 */
class JwtServiceTest {

    /**
     * 验证已签发的令牌可还原完整的可信主体信息。
     */
    @Test
    void shouldIssueAndParseAccessToken() {
        JwtService jwtService = new JwtService(createProperties(TEST_SIGNING_KEY));
        AuthPrincipal principal = new AuthPrincipal(
                UUID.randomUUID(),
                "admin",
                "tenant-a",
                Set.of("TENANT_ADMIN", "OPERATIONS")
        );

        String token = jwtService.issueAccessToken(principal, Instant.now());

        assertThat(jwtService.parseAccessToken(token)).isEqualTo(principal);
    }

    /**
     * 验证已经过期的 Access Token 无法通过校验。
     */
    @Test
    void shouldRejectExpiredAccessToken() {
        JwtProperties properties = createProperties(TEST_SIGNING_KEY);
        JwtService jwtService = new JwtService(properties);
        AuthPrincipal principal = new AuthPrincipal(
                UUID.randomUUID(),
                "admin",
                "tenant-a",
                Set.of("TENANT_ADMIN")
        );
        Instant issuedAt = Instant.now().minus(properties.getAccessTokenTtl()).minusSeconds(1);

        String token = jwtService.issueAccessToken(principal, issuedAt);

        assertThatThrownBy(() -> jwtService.parseAccessToken(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    /**
     * 验证使用其他密钥签名的令牌会被拒绝。
     */
    @Test
    void shouldRejectAccessTokenWithUnexpectedSignature() {
        JwtService issuer = new JwtService(createProperties(TEST_SIGNING_KEY));
        JwtService verifier = new JwtService(createProperties(OTHER_SIGNING_KEY));
        AuthPrincipal principal = new AuthPrincipal(
                UUID.randomUUID(),
                "admin",
                "tenant-a",
                Set.of("TENANT_ADMIN")
        );

        String token = issuer.issueAccessToken(principal, Instant.now());

        assertThatThrownBy(() -> verifier.parseAccessToken(token))
                .isInstanceOf(SignatureException.class);
    }

    private static final String TEST_SIGNING_KEY =
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private static final String OTHER_SIGNING_KEY =
            "ZmVkY2JhOTg3NjU0MzIxMGZlZGNiYTk4NzY1NDMyMTA=";

    private static JwtProperties createProperties(String signingKey) {
        JwtProperties properties = new JwtProperties();
        properties.setIssuer("flowmesh-test");
        properties.setSigningKey(signingKey);
        properties.setAccessTokenTtl(Duration.ofMinutes(15));
        properties.setRefreshTokenTtl(Duration.ofDays(7));
        return properties;
    }
}

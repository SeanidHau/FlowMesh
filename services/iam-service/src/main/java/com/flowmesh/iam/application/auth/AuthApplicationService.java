package com.flowmesh.iam.application.auth;

import com.flowmesh.common.security.AuthPrincipal;
import com.flowmesh.common.security.JwtProperties;
import com.flowmesh.common.security.JwtService;
import com.flowmesh.iam.domain.role.UserRole;
import com.flowmesh.iam.domain.token.RefreshToken;
import com.flowmesh.iam.domain.tenant.TenantStatus;
import com.flowmesh.iam.domain.user.IamUser;
import com.flowmesh.iam.domain.user.UserStatus;
import com.flowmesh.iam.repository.IamUserRepository;
import com.flowmesh.iam.repository.RefreshTokenRepository;
import com.flowmesh.iam.repository.UserRoleRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 处理登录、刷新令牌轮换和登出。
 *
 * <p>登录按租户和用户名解析用户，凭证校验失败统一抛出
 * {@link InvalidCredentialsException}。</p>
 */
@Service
public class AuthApplicationService {

    private static final Logger log = LoggerFactory.getLogger(AuthApplicationService.class);

    private final IamUserRepository iamUserRepository;
    private final UserRoleRepository userRoleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 创建认证应用服务。
     *
     * @param iamUserRepository 用户仓储
     * @param userRoleRepository 用户角色关系仓储
     * @param refreshTokenRepository 刷新令牌仓储
     * @param passwordEncoder BCrypt 密码编码器
     * @param jwtService JWT 签发服务
     * @param jwtProperties JWT 配置
     */
    public AuthApplicationService(
        IamUserRepository iamUserRepository,
        UserRoleRepository userRoleRepository,
        RefreshTokenRepository refreshTokenRepository,
        PasswordEncoder passwordEncoder,
        JwtService jwtService,
        JwtProperties jwtProperties
    ) {
        this.iamUserRepository = iamUserRepository;
        this.userRoleRepository = userRoleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
    }

    /**
     * 按用户名和密码执行登录，签发新的 Access Token 和 Refresh Token。
     *
     * @param tenantId 租户标识
     * @param username 用户名
     * @param password 密码明文
     * @return 令牌对
     * @throws InvalidCredentialsException 凭证无效
     */
    @Transactional
    public TokenResult login(String tenantId, String username, String password) {
        String normalized = IamUser.normalizeUsername(username);
        IamUser user = iamUserRepository
            .findByTenant_IdAndUsername(tenantId, normalized)
            .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        ensureCanAuthenticate(user);

        user.recordSuccessfulLogin(Instant.now());
        iamUserRepository.saveAndFlush(user);

        Set<String> roles = loadRoles(user.getId());
        AuthPrincipal principal = new AuthPrincipal(
            user.getId(), user.getUsername(), user.getTenant().getId(), roles
        );
        String accessToken = jwtService.issueAccessToken(principal, Instant.now());
        String refreshToken = createRefreshToken(user);

        return new TokenResult(accessToken, refreshToken);
    }

    /**
     * 用已持有的刷新令牌换取新的令牌对。旧令牌立即失效。
     *
     * @param rawToken 原始刷新令牌
     * @return 新的令牌对
     * @throws InvalidCredentialsException 令牌无效、过期或已撤销
     */
    @Transactional
    public TokenResult refresh(String rawToken) {
        String tokenHash = sha256Hex(rawToken);
        RefreshToken oldToken = refreshTokenRepository.findByTokenHashForUpdate(tokenHash)
            .orElseThrow(InvalidCredentialsException::new);

        if (!oldToken.isActiveAt(Instant.now())) {
            throw new InvalidCredentialsException();
        }

        IamUser user = oldToken.getUser();
        ensureCanAuthenticate(user);
        Set<String> roles = loadRoles(user.getId());
        AuthPrincipal principal = new AuthPrincipal(
            user.getId(), user.getUsername(), user.getTenant().getId(), roles
        );
        String accessToken = jwtService.issueAccessToken(principal, Instant.now());

        String newRefreshToken = generateRawToken();
        RefreshToken replacement = refreshTokenRepository.saveAndFlush(
            new RefreshToken(user, sha256Hex(newRefreshToken), Instant.now().plus(jwtProperties.getRefreshTokenTtl()))
        );
        oldToken.replaceWith(replacement, Instant.now());
        refreshTokenRepository.saveAndFlush(oldToken);

        return new TokenResult(accessToken, newRefreshToken);
    }

    /**
     * 撤销指定的刷新令牌。幂等：重复撤销不产生副作用。
     *
     * @param rawToken 原始刷新令牌
     */
    @Transactional
    public void logout(String rawToken) {
        String tokenHash = sha256Hex(rawToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.revoke(Instant.now());
                refreshTokenRepository.saveAndFlush(token);
                log.info("用户 {} 已登出，刷新令牌已撤销", token.getUser().getUsername());
            }
        });
    }

    private String createRefreshToken(IamUser user) {
        String rawToken = generateRawToken();
        refreshTokenRepository.saveAndFlush(
            new RefreshToken(user, sha256Hex(rawToken), Instant.now().plus(jwtProperties.getRefreshTokenTtl()))
        );
        return rawToken;
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private Set<String> loadRoles(UUID userId) {
        return userRoleRepository.findAllByUser_Id(userId).stream()
            .map(UserRole::getRole)
            .map(role -> role.getCode())
            .collect(Collectors.toUnmodifiableSet());
    }

    private static void ensureCanAuthenticate(IamUser user) {
        if (user.getStatus() != UserStatus.ACTIVE
            || user.getTenant().getStatus() != TenantStatus.ACTIVE) {
            throw new InvalidCredentialsException();
        }
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /**
     * 登录/刷新成功后返回的令牌对。
     *
     * @param accessToken Access Token
     * @param refreshToken Refresh Token
     */
    public record TokenResult(String accessToken, String refreshToken) {
    }
}

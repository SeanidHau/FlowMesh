package com.flowmesh.iam.application.auth;

import com.flowmesh.iam.config.LoginRateLimitProperties;
import com.flowmesh.iam.domain.user.IamUser;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/**
 * 基于 Redis 的登录尝试次数限流器。
 *
 * <p>Redis 只负责限流辅助，不参与用户身份判断。Redis 不可用时降级放行，
 * 避免缓存故障阻断正常认证。</p>
 */
@Service
public class LoginRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimiter.class);

    private static final DefaultRedisScript<Long> CHECK_AND_RESERVE_SCRIPT = new DefaultRedisScript<>(
        "local account = redis.call('GET', KEYS[1]) "
            + "local client = redis.call('GET', KEYS[2]) "
            + "if (account and tonumber(account) >= tonumber(ARGV[2])) "
            + "or (client and tonumber(client) >= tonumber(ARGV[3])) then return 0 end "
            + "local accountCount = redis.call('INCR', KEYS[1]) "
            + "if accountCount == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
            + "local clientCount = redis.call('INCR', KEYS[2]) "
            + "if clientCount == 1 then redis.call('EXPIRE', KEYS[2], ARGV[1]) end "
            + "return 1",
        Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final LoginRateLimitProperties properties;

    /**
     * 创建登录限流器。
     *
     * @param redisTemplate Redis 字符串模板
     * @param properties 登录限流配置
     */
    public LoginRateLimiter(
        StringRedisTemplate redisTemplate,
        LoginRateLimitProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    /**
     * 原子检查并占用一次账号和客户端的登录尝试额度。
     *
     * @param attempt 登录尝试信息
     * @throws LoginRateLimitExceededException 任一维度超过阈值
     */
    public void check(LoginAttempt attempt) {
        if (!properties.enabled()) {
            return;
        }

        try {
            Long reserved = redisTemplate.execute(
                CHECK_AND_RESERVE_SCRIPT,
                java.util.List.of(accountKey(attempt), clientKey(attempt)),
                Long.toString(Math.max(1L, properties.window().toSeconds())),
                Integer.toString(properties.accountMaxAttempts()),
                Integer.toString(properties.clientMaxAttempts())
            );
            if (Long.valueOf(0L).equals(reserved)) {
                throw new LoginRateLimitExceededException();
            }
        } catch (DataAccessException | NumberFormatException exception) {
            log.warn("Redis 登录限流检查失败，已降级放行。", exception);
        }
    }

    /**
     * 清除指定账号的登录尝试计数。
     *
     * @param tenantId 租户标识
     * @param username 用户名
     */
    public void clearAccount(String tenantId, String username) {
        if (!properties.enabled()) {
            return;
        }

        try {
            redisTemplate.delete(accountKey(tenantId, username));
        } catch (DataAccessException exception) {
            log.warn("Redis 登录尝试计数清理失败。", exception);
        }
    }

    private static String accountKey(LoginAttempt attempt) {
        return accountKey(attempt.tenantId(), attempt.username());
    }

    private static String accountKey(String tenantId, String username) {
        return "flowmesh:iam:login:attempt:account:"
            + tenantId + ":" + IamUser.normalizeUsername(username);
    }

    private static String clientKey(LoginAttempt attempt) {
        return "flowmesh:iam:login:attempt:client:"
            + Objects.requireNonNullElse(attempt.clientAddress(), "unknown");
    }

    /**
     * 表示一次登录尝试。
     *
     * @param tenantId 租户标识
     * @param username 用户名
     * @param clientAddress 客户端地址
     */
    public record LoginAttempt(
        String tenantId,
        String username,
        String clientAddress
    ) {
    }
}

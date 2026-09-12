package com.flowmesh.iam.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 登录尝试限流配置。
 *
 * @param enabled 是否启用限流
 * @param accountMaxAttempts 单个账号在窗口内允许的最大尝试次数
 * @param clientMaxAttempts 单个客户端地址在窗口内允许的最大尝试次数
 * @param window 限流时间窗口
 * @param failOpen Redis 不可用时是否降级放行；生产环境建议关闭
 */
@ConfigurationProperties(prefix = "flowmesh.security.login-rate-limit")
public record LoginRateLimitProperties(
    boolean enabled,
    int accountMaxAttempts,
    int clientMaxAttempts,
    Duration window,
    boolean failOpen
) {

    /**
     * 在限流功能启用时校验配置，避免零值或负值导致所有请求被错误拒绝，
     * 或 Redis 使用无效的过期时间。
     */
    public LoginRateLimitProperties {
        if (enabled && accountMaxAttempts < 1) {
            throw new IllegalArgumentException("account-max-attempts must be positive");
        }
        if (enabled && clientMaxAttempts < 1) {
            throw new IllegalArgumentException("client-max-attempts must be positive");
        }
        if (enabled && (window == null || window.isZero() || window.isNegative())) {
            throw new IllegalArgumentException("window must be positive");
        }
    }

    /**
     * 计算 HTTP {@code Retry-After} 所需的向上取整秒数。
     *
     * @return 至少为 1 的重试等待秒数
     */
    public long retryAfterSeconds() {
        long seconds = window.toSeconds();
        boolean hasSubSecondPart = !window.minusSeconds(seconds).isZero();
        return Math.max(1L, hasSubSecondPart ? seconds + 1L : seconds);
    }
}

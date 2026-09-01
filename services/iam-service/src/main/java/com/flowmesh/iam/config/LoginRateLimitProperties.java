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
 */
@ConfigurationProperties(prefix = "flowmesh.security.login-rate-limit")
public record LoginRateLimitProperties(
    boolean enabled,
    int accountMaxAttempts,
    int clientMaxAttempts,
    Duration window
) {
}

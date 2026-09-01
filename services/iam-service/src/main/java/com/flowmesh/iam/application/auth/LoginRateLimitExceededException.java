package com.flowmesh.iam.application.auth;

/**
 * 表示登录请求超过 Redis 限流阈值。
 */
public class LoginRateLimitExceededException extends RuntimeException {

    /**
     * 创建登录限流异常。
     */
    public LoginRateLimitExceededException() {
        super("登录尝试过于频繁，请稍后再试。");
    }
}

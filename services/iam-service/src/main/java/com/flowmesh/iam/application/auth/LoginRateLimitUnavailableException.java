package com.flowmesh.iam.application.auth;

/**
 * Redis 限流依赖不可用且配置为拒绝放行时抛出的异常。
 */
public class LoginRateLimitUnavailableException extends RuntimeException {
}

package com.flowmesh.common.security;

import java.util.Set;
import java.util.UUID;

/**
 * 表示已完成认证和授权验证的请求主体。
 *
 * <p>租户标识必须来自已签名的 JWT Claim。业务服务不得信任客户端自行提交的
 * {@code tenantId} 请求参数或 Header。</p>
 *
 * @param userId 用户唯一标识
 * @param username 用户名
 * @param tenantId 租户唯一标识
 * @param roles 用户已授予的角色集合
 */
public record AuthPrincipal(
    UUID userId,
    String username,
    String tenantId,
    Set<String> roles
) {
}

package com.flowmesh.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

import java.util.List;

/**
 * Gateway 限流组件配置。
 *
 * <p>RateLimiter 本身由 Spring Cloud Gateway 自动配置，本文只提供不依赖认证状态的客户端
 * key resolver，使登录等匿名请求也能受到保护。</p>
 */
@Configuration(proxyBeanMethods = false)
public class GatewayRateLimitConfiguration {

    /**
     * 注册客户端地址 key resolver，供 Gateway 限流过滤器使用。
     *
     * @param clientIpHeader 可信入口写入的客户端地址请求头
     * @return Gateway Redis 限流 key resolver
     */
    @Bean
    public GatewayClientIpKeyResolver flowMeshRateLimitKeyResolver(
        @Value("${flowmesh.gateway.rate-limit.client-ip-header:X-Real-IP}") String clientIpHeader
    ) {
        return new GatewayClientIpKeyResolver(clientIpHeader);
    }

    /**
     * 注册令牌桶 Lua 脚本，保证同一个客户端的扣减和过期时间在 Redis 内原子执行。
     *
     * @return Redis Lua 脚本
     */
    @Bean
    public RedisScript<List<Long>> gatewayRateLimitScript() {
        DefaultRedisScript<List<Long>> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(
            new ClassPathResource("redis/gateway_rate_limiter.lua")
        ));
        @SuppressWarnings("unchecked")
        Class<List<Long>> resultType = (Class<List<Long>>) (Class<?>) List.class;
        script.setResultType(resultType);
        return script;
    }
}

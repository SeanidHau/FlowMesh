package com.flowmesh.gateway;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * 使用 Redis Lua 令牌桶保护 Gateway 业务入口。
 *
 * <p>与 Spring Cloud Gateway 默认 RedisRateLimiter 不同，本过滤器在 Redis 执行失败时明确返回
 * {@code 503}，避免依赖故障时入口保护悄悄退化为放行。Actuator 健康检查不经过限流，确保
 * Kubernetes 能够区分应用存活和 Redis 依赖状态。</p>
 */
@Component
public final class GatewayRedisRateLimitGlobalFilter implements GlobalFilter, Ordered {

    /** Redis Lua 脚本使用的限流 key 命名空间。 */
    private static final String REDIS_KEY_PREFIX = "request_rate_limiter.";

    /** 令牌桶返回的允许标记。 */
    private static final long ALLOWED = 1L;

    /** 限流响应的重试提示秒数。 */
    private static final String RETRY_AFTER_SECONDS = "1";

    private static final Logger log = LoggerFactory.getLogger(GatewayRedisRateLimitGlobalFilter.class);

    private final ReactiveStringRedisTemplate redisTemplate;
    private final RedisScript<List<Long>> rateLimitScript;
    private final GatewayClientIpKeyResolver keyResolver;
    private final int replenishRate;
    private final int burstCapacity;
    private final int requestedTokens;

    /**
     * 创建 Gateway Redis 限流过滤器。
     *
     * @param redisTemplate 响应式 Redis 客户端
     * @param rateLimitScript 原子令牌桶脚本
     * @param keyResolver 客户端地址 key 解析器
     * @param replenishRate 每秒补充的令牌数
     * @param burstCapacity 令牌桶容量
     * @param requestedTokens 单个请求消耗的令牌数
     */
    public GatewayRedisRateLimitGlobalFilter(
        ReactiveStringRedisTemplate redisTemplate,
        RedisScript<List<Long>> rateLimitScript,
        GatewayClientIpKeyResolver keyResolver,
        @Value("${flowmesh.gateway.rate-limit.replenish-rate:100}") int replenishRate,
        @Value("${flowmesh.gateway.rate-limit.burst-capacity:200}") int burstCapacity,
        @Value("${flowmesh.gateway.rate-limit.requested-tokens:1}") int requestedTokens
    ) {
        this.redisTemplate = redisTemplate;
        this.rateLimitScript = rateLimitScript;
        this.keyResolver = keyResolver;
        this.replenishRate = requirePositive("replenishRate", replenishRate);
        this.burstCapacity = requirePositive("burstCapacity", burstCapacity);
        this.requestedTokens = requirePositive("requestedTokens", requestedTokens);
    }

    /**
     * 在所有业务路由转发前执行限流。
     *
     * @param exchange 当前响应式 HTTP 交换对象
     * @param chain Gateway 过滤器链
     * @return 异步过滤结果
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
        if (isActuatorRequest(exchange)) {
            return chain.filter(exchange);
        }

        String routeId = resolveRouteId(exchange);
        return keyResolver.resolve(exchange)
            .flatMap(clientKey -> executeRateLimit(routeId, clientKey))
            .flatMap(result -> {
                if (result.isAllowed()) {
                    addRateLimitHeaders(exchange.getResponse(), result.remainingTokens());
                    return chain.filter(exchange);
                }
                return reject(exchange.getResponse(), HttpStatus.TOO_MANY_REQUESTS, result.remainingTokens());
            })
            .onErrorResume(error -> {
                log.error("Gateway Redis rate limiter unavailable; rejecting request", error);
                return reject(exchange.getResponse(), HttpStatus.SERVICE_UNAVAILABLE, -1L);
            });
    }

    /**
     * 让 Trace ID 过滤器先执行，但在路由转发前完成限流。
     *
     * @return 过滤器顺序
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    /**
     * 执行单次令牌桶扣减；时间参数留空，由 Redis Lua 脚本调用 Redis TIME 获取服务端时间，
     * 避免不同 Gateway Pod 的本地时钟漂移影响分布式限流结果。
     *
     * @param routeId 当前 Gateway 路由标识
     * @param clientKey 客户端限流 key
     * @return 限流执行结果
     */
    private Mono<RateLimitResult> executeRateLimit(String routeId, String clientKey) {
        String bucketKey = REDIS_KEY_PREFIX + routeId + "_" + clientKey;
        List<String> keys = List.of(bucketKey + ".tokens", bucketKey + ".timestamp");
        List<String> arguments = List.of(
            Integer.toString(replenishRate),
            Integer.toString(burstCapacity),
            "",
            Integer.toString(requestedTokens)
        );
        return redisTemplate.execute(rateLimitScript, keys, arguments)
            .collectList()
            .flatMap(this::toResult);
    }

    private Mono<RateLimitResult> toResult(List<List<Long>> scriptResults) {
        if (scriptResults.isEmpty() || scriptResults.get(0) == null || scriptResults.get(0).size() < 2) {
            return Mono.error(new IllegalStateException("Redis rate limiter returned an invalid result"));
        }
        List<Long> result = scriptResults.get(0);
        return Mono.just(new RateLimitResult(result.get(0) == ALLOWED, result.get(1)));
    }

    private Mono<Void> reject(ServerHttpResponse response, HttpStatus status, long remainingTokens) {
        addRateLimitHeaders(response, remainingTokens);
        if (status == HttpStatus.TOO_MANY_REQUESTS) {
            response.getHeaders().set("Retry-After", RETRY_AFTER_SECONDS);
        }
        response.setStatusCode(status);
        return response.setComplete();
    }

    private void addRateLimitHeaders(ServerHttpResponse response, long remainingTokens) {
        response.getHeaders().set("X-RateLimit-Limit", Integer.toString(burstCapacity));
        response.getHeaders().set("X-RateLimit-Remaining", Long.toString(remainingTokens));
    }

    private String resolveRouteId(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        return route == null ? "unmatched" : route.getId();
    }

    private boolean isActuatorRequest(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        return path.equals("/actuator") || path.startsWith("/actuator/");
    }

    private int requirePositive(String name, int value) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private record RateLimitResult(boolean isAllowed, long remainingTokens) {
    }
}

package com.flowmesh.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 验证 Gateway Redis 限流的放行、超额拒绝和 Redis 故障保护行为。
 */
class GatewayRedisRateLimitGlobalFilterTest {

    /**
     * Redis 返回允许时应继续执行 Gateway 过滤器链，并回写剩余令牌。
     */
    @Test
    void shouldContinueWhenRedisAllowsRequest() {
        ReactiveStringRedisTemplate redisTemplate = mock(ReactiveStringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        RedisScript<List<Long>> script = mock(RedisScript.class);
        when(redisTemplate.execute(eq(script), anyList(), anyList()))
            .thenReturn(Flux.just(List.of(1L, 199L)));
        GatewayClientIpKeyResolver keyResolver = new GatewayClientIpKeyResolver("X-Real-IP");
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        GatewayRedisRateLimitGlobalFilter filter = new GatewayRedisRateLimitGlobalFilter(
            redisTemplate, script, keyResolver, meterRegistry, 100, 200, 1
        );
        MockServerWebExchange exchange = exchange();
        GatewayFilterChain chain = nextExchange -> Mono.empty();

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
        assertThat(exchange.getResponse().getHeaders().getFirst("X-RateLimit-Remaining"))
            .isEqualTo("199");
        assertThat(meterRegistry.get("flowmesh.gateway.rate.limit.requests")
            .tag("route", "unmatched").tag("outcome", "allowed").counter().count())
            .isEqualTo(1.0);
    }

    /**
     * Redis 返回额度不足时应阻断下游并返回标准 429。
     */
    @Test
    void shouldRejectWhenRedisDeniesRequest() {
        ReactiveStringRedisTemplate redisTemplate = mock(ReactiveStringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        RedisScript<List<Long>> script = mock(RedisScript.class);
        when(redisTemplate.execute(eq(script), anyList(), anyList()))
            .thenReturn(Flux.just(List.of(0L, 0L)));
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        GatewayRedisRateLimitGlobalFilter filter = new GatewayRedisRateLimitGlobalFilter(
            redisTemplate, script, new GatewayClientIpKeyResolver("X-Real-IP"),
            meterRegistry, 100, 200, 1
        );
        MockServerWebExchange exchange = exchange();
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("1");
        assertThat(meterRegistry.get("flowmesh.gateway.rate.limit.requests")
            .tag("route", "unmatched").tag("outcome", "denied").counter().count())
            .isEqualTo(1.0);
        verify(chain, never()).filter(exchange);
    }

    /**
     * Redis 执行失败时应 fail-closed 返回 503，而不是把请求转发到下游。
     */
    @Test
    void shouldFailClosedWhenRedisFails() {
        ReactiveStringRedisTemplate redisTemplate = mock(ReactiveStringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        RedisScript<List<Long>> script = mock(RedisScript.class);
        when(redisTemplate.execute(eq(script), anyList(), anyList()))
            .thenReturn(Flux.error(new IllegalStateException("redis unavailable")));
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        GatewayRedisRateLimitGlobalFilter filter = new GatewayRedisRateLimitGlobalFilter(
            redisTemplate, script, new GatewayClientIpKeyResolver("X-Real-IP"),
            meterRegistry, 100, 200, 1
        );
        MockServerWebExchange exchange = exchange();
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(meterRegistry.get("flowmesh.gateway.rate.limit.requests")
            .tag("route", "unmatched").tag("outcome", "redis_error").counter().count())
            .isEqualTo(1.0);
        verify(chain, never()).filter(exchange);
    }

    private MockServerWebExchange exchange() {
        return MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/iam/api/v1/auth/login")
                .header("X-Real-IP", "203.0.113.10")
                .build()
        );
    }
}

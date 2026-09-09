package com.flowmesh.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * 验证 Gateway Trace ID 的生成、透传和响应回写行为。
 */
class GatewayTraceIdGlobalFilterTest {

    /**
     * 已有 Trace ID 应原样透传给下游并回写响应。
     */
    @Test
    void shouldPropagateExistingTraceId() {
        String traceId = "trace-from-client";
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/iam/api/v1/auth/login")
                .header(GatewayTraceIdGlobalFilter.TRACE_ID_HEADER, traceId)
                .build()
        );
        GatewayTraceIdGlobalFilter filter = new GatewayTraceIdGlobalFilter();
        GatewayFilterChain chain = nextExchange -> {
            assertThat(nextExchange.getRequest().getHeaders().getFirst(
                GatewayTraceIdGlobalFilter.TRACE_ID_HEADER
            )).isEqualTo(traceId);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getHeaders().getFirst(
            GatewayTraceIdGlobalFilter.TRACE_ID_HEADER
        )).isEqualTo(traceId);
    }

    /**
     * 缺失 Trace ID 时应生成可用于日志关联的新 UUID。
     */
    @Test
    void shouldGenerateTraceIdWhenHeaderIsMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/iam/api/v1/auth/login").build()
        );
        GatewayTraceIdGlobalFilter filter = new GatewayTraceIdGlobalFilter();
        GatewayFilterChain chain = nextExchange -> {
            String traceId = nextExchange.getRequest().getHeaders().getFirst(
                GatewayTraceIdGlobalFilter.TRACE_ID_HEADER
            );
            assertThat(traceId).isNotBlank();
            assertThat(traceId).hasSize(36);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getHeaders().getFirst(
            GatewayTraceIdGlobalFilter.TRACE_ID_HEADER
        )).isNotBlank();
    }
}

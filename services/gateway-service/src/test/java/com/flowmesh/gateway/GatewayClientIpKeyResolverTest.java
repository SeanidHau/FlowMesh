package com.flowmesh.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

/**
 * 验证 Gateway 限流 key 的客户端地址选择和缺省回退行为。
 */
class GatewayClientIpKeyResolverTest {

    /**
     * 配置的可信请求头存在时，应只使用其第一个地址，避免代理链导致 key 漂移。
     */
    @Test
    void shouldUseFirstConfiguredClientIpHeaderValue() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/iam/api/v1/auth/login")
                .header("X-Real-IP", " 203.0.113.10, 10.0.0.1")
                .remoteAddress(new InetSocketAddress("192.0.2.1", 4567))
                .build()
        );

        String key = new GatewayClientIpKeyResolver("X-Real-IP").resolve(exchange).block();

        assertThat(key).isEqualTo("flowmesh:gateway:client-ip:203.0.113.10");
    }

    /**
     * 可信请求头缺失时，应回退到 TCP 对端地址而不是生成空 key。
     */
    @Test
    void shouldFallbackToRemoteAddress() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/iam/api/v1/auth/login")
                .remoteAddress(new InetSocketAddress("192.0.2.2", 4567))
                .build()
        );

        String key = new GatewayClientIpKeyResolver("X-Real-IP").resolve(exchange).block();

        assertThat(key).isEqualTo("flowmesh:gateway:client-ip:192.0.2.2");
    }
}

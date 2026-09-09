package com.flowmesh.gateway;

import java.util.UUID;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.cloud.gateway.filter.GlobalFilter;

import reactor.core.publisher.Mono;

/**
 * 为网关请求生成或透传 Trace ID，并把它交给下游服务。
 *
 * <p>Trace ID 只用于链路关联，不承担认证或租户身份职责。</p>
 */
@Component
public class GatewayTraceIdGlobalFilter implements GlobalFilter, Ordered {

    /** 下游服务统一读取的 Trace ID 请求头。 */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /** Trace ID 的最大允许长度，与 Servlet 服务保持一致。 */
    private static final int MAX_TRACE_ID_LENGTH = 128;

    /**
     * 在路由前将 Trace ID 注入下游请求，在响应中回传给调用方。
     *
     * @param exchange 当前响应式 HTTP 交换对象
     * @param chain 网关过滤器链
     * @return 异步过滤结果
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = resolveTraceId(exchange.getRequest());
        ServerHttpRequest request = exchange.getRequest().mutate()
            .headers(headers -> {
                headers.remove(TRACE_ID_HEADER);
                headers.add(TRACE_ID_HEADER, traceId);
            })
            .build();
        ServerWebExchange forwardedExchange = exchange.mutate().request(request).build();
        exchange.getResponse().getHeaders().set(TRACE_ID_HEADER, traceId);
        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().getHeaders().set(TRACE_ID_HEADER, traceId);
            return Mono.empty();
        });
        return chain.filter(forwardedExchange);
    }

    /**
     * 让 Trace ID 过滤器尽早运行，保证所有路由都能继承同一个链路标识。
     *
     * @return 过滤器顺序
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private String resolveTraceId(ServerHttpRequest request) {
        String traceId = request.getHeaders().getFirst(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank() || traceId.length() > MAX_TRACE_ID_LENGTH) {
            return UUID.randomUUID().toString();
        }
        return traceId;
    }
}

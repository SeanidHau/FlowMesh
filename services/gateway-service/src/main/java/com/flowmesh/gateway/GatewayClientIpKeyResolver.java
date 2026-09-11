package com.flowmesh.gateway;

import java.net.InetSocketAddress;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * 为 Gateway Redis 限流生成客户端维度的稳定 key。
 *
 * <p>生产环境通常由 Ingress 覆写可信的客户端地址请求头，Gateway 只读取部署配置指定的
 * 一个请求头；请求头缺失时回退到 TCP 对端地址，避免产生空 key 绕过限流。</p>
 */
public final class GatewayClientIpKeyResolver implements KeyResolver {

    /** Redis key 的固定前缀，避免与其他限流或业务 key 冲突。 */
    private static final String KEY_PREFIX = "flowmesh:gateway:client-ip:";

    /** 防止被伪造的请求头制造异常长 Redis key。 */
    private static final int MAX_CLIENT_ADDRESS_LENGTH = 128;

    /** 由可信入口写入的客户端地址请求头名称。 */
    private final String clientIpHeader;

    /**
     * 创建客户端地址 key 解析器。
     *
     * @param clientIpHeader 可信入口写入的客户端地址请求头；为空时只使用 TCP 对端地址
     */
    public GatewayClientIpKeyResolver(String clientIpHeader) {
        this.clientIpHeader = clientIpHeader == null ? "" : clientIpHeader.trim();
    }

    /**
     * 从请求头或 TCP 对端地址解析限流 key。
     *
     * @param exchange 当前响应式 HTTP 交换对象
     * @return 非空且稳定的 Redis 限流 key
     */
    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        String clientAddress = firstHeaderValue(request, clientIpHeader);
        if (clientAddress == null) {
            clientAddress = resolveRemoteAddress(request);
        }
        return Mono.just(KEY_PREFIX + clientAddress);
    }

    private String firstHeaderValue(ServerHttpRequest request, String headerName) {
        if (headerName.isBlank()) {
            return null;
        }
        String value = request.getHeaders().getFirst(headerName);
        if (value == null || value.isBlank()) {
            return null;
        }
        String firstValue = value.split(",", 2)[0].trim();
        return firstValue.isBlank() || firstValue.length() > MAX_CLIENT_ADDRESS_LENGTH
            ? null
            : firstValue;
    }

    private String resolveRemoteAddress(ServerHttpRequest request) {
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress == null) {
            return "unknown";
        }
        if (remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }
        String hostName = remoteAddress.getHostString();
        return hostName == null || hostName.isBlank() ? "unknown" : hostName;
    }
}

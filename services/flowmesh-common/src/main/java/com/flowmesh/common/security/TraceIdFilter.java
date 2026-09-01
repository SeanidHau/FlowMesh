package com.flowmesh.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 为 HTTP 请求建立可传播的 Trace ID，并在日志上下文中暂存。
 *
 * <p>客户端提供的标识仅在非空且长度受限时复用，否则生成新的 UUID，避免将过长输入
 * 或空值写入日志和下游事件。</p>
 */
public class TraceIdFilter extends OncePerRequestFilter {

    /**
     * Trace ID 请求头名称。
     */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader(TRACE_ID_HEADER);
        String traceId = header == null || header.isBlank() || header.length() > 128
            ? UUID.randomUUID().toString() : header;
        response.setHeader(TRACE_ID_HEADER, traceId);
        try (MDC.MDCCloseable ignored = MDC.putCloseable("traceId", traceId)) {
            filterChain.doFilter(request, response);
        }
    }
}

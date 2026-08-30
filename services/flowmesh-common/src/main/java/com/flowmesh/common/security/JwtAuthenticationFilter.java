package com.flowmesh.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmesh.common.api.ErrorResponse;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 解析 Bearer Access Token 并填充 SecurityContext。
 *
 * <p>过滤器从 Authorization 头提取 Bearer 令牌，调用 {@link JwtService} 校验，
 * 成功后将 {@link AuthPrincipal} 设为 SecurityContext 的 principal。解析失败时不放行，
 * 直接返回 401 ErrorResponse，不交由后续异常处理。</p>
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    /**
     * 创建 JWT 认证过滤器。
     *
     * @param jwtService JWT 签发与校验服务
     * @param objectMapper JSON 序列化器
     */
    public JwtAuthenticationFilter(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        AuthPrincipal principal;
        try {
            principal = jwtService.parseAccessToken(token);
        } catch (JwtException | IllegalArgumentException | NullPointerException exception) {
            writeUnauthorized(response, request);
            return;
        }

        var authorities = principal.roles().stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
            .map(a -> (org.springframework.security.core.GrantedAuthority) a)
            .toList();
        var authentication = new UsernamePasswordAuthenticationToken(
            principal, null, authorities
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response, HttpServletRequest request) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String traceId = request.getHeader(TRACE_ID_HEADER);
        ErrorResponse body = ErrorResponse.of(
            "UNAUTHORIZED",
            "Access Token 缺失或已失效。",
            traceId != null ? traceId : ""
        );
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}

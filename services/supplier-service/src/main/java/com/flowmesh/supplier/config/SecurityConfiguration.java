package com.flowmesh.supplier.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmesh.common.api.ErrorResponse;
import com.flowmesh.common.security.JwtAuthenticationFilter;
import com.flowmesh.common.security.JwtProperties;
import com.flowmesh.common.security.JwtService;
import com.flowmesh.common.security.TraceIdFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.nio.charset.StandardCharsets;

/**
 * 配置 supplier 服务的 HTTP 安全边界。
 *
 * <p>复用 common 的 {@link JwtAuthenticationFilter} 解析 Access Token。
 * 健康检查放行；写操作需认证且持有 APPLICANT 角色；其余路径默认要求认证。</p>
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfiguration {

    /**
     * 创建 JWT 认证过滤器。
     *
     * @param jwtService JWT 签发与校验服务
     * @param objectMapper JSON 序列化器
     * @return JWT 认证过滤器
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(
        JwtService jwtService,
        ObjectMapper objectMapper
    ) {
        return new JwtAuthenticationFilter(jwtService, objectMapper);
    }

    /**
     * 创建 HTTP Trace ID 过滤器。
     *
     * @return Trace ID 过滤器
     */
    @Bean
    public TraceIdFilter traceIdFilter() {
        return new TraceIdFilter();
    }

    /**
     * 创建 supplier 服务的安全过滤器链。
     *
     * @param http Spring Security 的 HTTP 配置对象
     * @param jwtAuthenticationFilter JWT 认证过滤器
     * @param objectMapper JSON 序列化器
     * @return 已配置的安全过滤器链
     * @throws Exception 当 Spring Security 无法构建过滤器链时抛出
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        JwtAuthenticationFilter jwtAuthenticationFilter,
        ObjectMapper objectMapper
    ) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                .requestMatchers("/api/v1/operations/**").hasRole("OPERATIONS")
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/supplier-applications")
                    .hasRole("APPLICANT")
                .anyRequest().authenticated()
            )
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .exceptionHandling(exceptionHandling ->
                exceptionHandling.authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    String traceId = request.getHeader("X-Trace-Id");
                    ErrorResponse body = ErrorResponse.of(
                        "UNAUTHORIZED",
                        "Access Token 缺失或已失效。",
                        traceId != null ? traceId : ""
                    );
                    response.getWriter().write(objectMapper.writeValueAsString(body));
                }).accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    String traceId = request.getHeader("X-Trace-Id");
                    ErrorResponse body = ErrorResponse.of(
                        "FORBIDDEN",
                        "角色或租户不允许执行此操作。",
                        traceId != null ? traceId : ""
                    );
                    response.getWriter().write(objectMapper.writeValueAsString(body));
                })
            )
            .addFilterBefore(
                traceIdFilter(),
                UsernamePasswordAuthenticationFilter.class
            )
            .addFilterBefore(
                jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class
            )
            .build();
    }
}

package com.flowmesh.workflow.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmesh.common.api.ErrorResponse;
import com.flowmesh.common.security.JwtAuthenticationFilter;
import com.flowmesh.common.security.JwtProperties;
import com.flowmesh.common.security.JwtService;
import com.flowmesh.common.security.TraceIdFilter;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 配置 workflow 服务的无状态 JWT 安全边界。
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfiguration {

    /**
     * 创建公共 JWT 认证过滤器。
     *
     * @param jwtService JWT 服务
     * @param objectMapper JSON 序列化器
     * @return JWT 过滤器
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
     * 创建 workflow HTTP 安全链。
     *
     * @param http HTTP 安全配置
     * @param jwtAuthenticationFilter JWT 过滤器
     * @param objectMapper JSON 序列化器
     * @return 安全过滤器链
     * @throws Exception 构建安全链失败
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        JwtAuthenticationFilter jwtAuthenticationFilter,
        ObjectMapper objectMapper
    ) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                .requestMatchers("/api/v1/operations/**").hasRole("OPERATIONS")
                .requestMatchers("/internal/v1/reconciliation/**").hasRole("OPERATIONS")
                .anyRequest().authenticated()
            )
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, exception) -> writeError(
                    response, objectMapper, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Access Token 缺失或已失效。"
                ))
                .accessDeniedHandler((request, response, exception) -> writeError(
                    response, objectMapper, HttpStatus.FORBIDDEN, "FORBIDDEN", "角色或租户不允许执行此操作。"
                ))
            )
            .addFilterBefore(traceIdFilter(), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    private static void writeError(
        jakarta.servlet.http.HttpServletResponse response,
        ObjectMapper objectMapper,
        HttpStatus status,
        String code,
        String message
    ) throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(ErrorResponse.of(code, message, "")));
    }
}

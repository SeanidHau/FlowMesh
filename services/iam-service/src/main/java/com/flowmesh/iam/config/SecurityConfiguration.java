package com.flowmesh.iam.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmesh.common.api.ErrorResponse;
import com.flowmesh.common.security.JwtAuthenticationFilter;
import com.flowmesh.common.security.JwtProperties;
import com.flowmesh.common.security.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.nio.charset.StandardCharsets;

/**
 * 配置 IAM 服务的 HTTP 安全边界。
 *
 * <p>服务使用无状态 JWT 认证模型。健康检查和认证入口可匿名访问，
 * 其他路径默认要求认证。未认证请求返回 401 ErrorResponse。</p>
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfiguration {

    /**
     * 创建 JWT 认证过滤器。各服务通过 @Bean 注册，避免 Servlet 容器重复注册。
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
     * 创建 IAM 服务的安全过滤器链。
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
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers("/api/v1/auth/**").permitAll()
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
                })
            )
            .addFilterBefore(
                jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class
            )
            .build();
    }

    /**
     * 创建用于保存和校验用户密码的 BCrypt 编码器。
     *
     * @return BCrypt 密码编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

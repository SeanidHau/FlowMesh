package com.flowmesh.iam.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 验证 IAM 服务的匿名访问边界。
 *
 * <p>该测试从 Spring 上下文启动 HTTP 安全过滤器链，确保运维端点保持可访问，
 * 同时未认证用户不能访问受保护的 API 路径。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigurationIntegrationTest {

    /** Spring Boot 配置的 MVC 测试客户端。 */
    @Autowired
    private MockMvc mockMvc;

    /**
     * 验证 Kubernetes 健康检查端点允许匿名访问。
     *
     * @throws Exception 当模拟 HTTP 请求执行失败时抛出
     */
    @Test
    void shouldAllowAnonymousAccessToHealthEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    /**
     * 验证信息端点允许匿名访问。
     *
     * @throws Exception 当模拟 HTTP 请求执行失败时抛出
     */
    @Test
    void shouldAllowAnonymousAccessToInfoEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());
    }

    /**
     * 验证匿名用户访问受保护 API 时返回 {@code 401 Unauthorized}。
     *
     * @throws Exception 当模拟 HTTP 请求执行失败时抛出
     */
    @Test
    void shouldRejectAnonymousAccessToProtectedApi() throws Exception {
        mockMvc.perform(get("/api/v1/unknown"))
                .andExpect(status().isUnauthorized());
    }
}

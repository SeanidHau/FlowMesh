package com.flowmesh.workflow.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowmesh.common.security.AuthPrincipal;
import com.flowmesh.workflow.support.PostgresIntegrationTest;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import com.flowmesh.common.security.JwtService;

/**
 * 验证 workflow 内部对账接口的运行时安全边界。
 *
 * <p>测试通过 MockMvc 穿过完整的 Spring Security 过滤器链，确认接口不会因为控制器
 * 被直接暴露而绕过 {@code OPERATIONS} 角色校验。</p>
 */
@AutoConfigureMockMvc
class WorkflowSecurityIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    /**
     * 验证匿名请求访问内部对账接口返回 401。
     *
     * @throws Exception 当请求执行失败时抛出
     */
    @Test
    void shouldRejectAnonymousInternalReconciliationRequest() throws Exception {
        mockMvc.perform(get("/internal/v1/reconciliation/workflow-instances/{applicationId}", UUID.randomUUID()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    /**
     * 验证没有 OPERATIONS 角色的已认证用户访问内部对账接口返回 403。
     *
     * @throws Exception 当请求执行失败时抛出
     */
    @Test
    void shouldRejectNonOperationsInternalReconciliationRequest() throws Exception {
        AuthPrincipal principal = new AuthPrincipal(
            UUID.randomUUID(), "applicant-a", "tenant-a", Set.of("APPLICANT")
        );
        String token = jwtService.issueAccessToken(principal, Instant.now());

        mockMvc.perform(get("/internal/v1/reconciliation/workflow-instances/{applicationId}", UUID.randomUUID())
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}

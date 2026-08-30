package com.flowmesh.supplier.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmesh.common.security.AuthPrincipal;
import com.flowmesh.common.security.JwtProperties;
import com.flowmesh.common.security.JwtService;
import com.flowmesh.supplier.support.PostgresIntegrationTest;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 验证 supplier 服务最小安全边界：无 token 401、非 APPLICANT 角色 403。
 */
@AutoConfigureMockMvc
class SupplierSecurityIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtProperties jwtProperties;

    /**
     * 验证无 Bearer Token 访问 POST /api/v1/supplier-applications 返回 401。
     *
     * @throws Exception 当请求执行失败时抛出
     */
    @Test
    void shouldReturn401WithoutToken() throws Exception {
        mockMvc.perform(post("/api/v1/supplier-applications")
                .header("Idempotency-Key", "test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"supplierName\":\"测试供应商\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").exists());
    }

    /**
     * 验证持有 PURCHASER 角色 token（非 APPLICANT）访问写操作返回 403。
     *
     * @throws Exception 当请求执行失败时抛出
     */
    @Test
    void shouldReturn403ForNonApplicantRole() throws Exception {
        AuthPrincipal principal = new AuthPrincipal(
            UUID.randomUUID(), "purchaser-a", "tenant-a", Set.of("PURCHASER")
        );
        String token = jwtService.issueAccessToken(principal, java.time.Instant.now());

        mockMvc.perform(post("/api/v1/supplier-applications")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"supplierName\":\"测试供应商\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}

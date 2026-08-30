package com.flowmesh.iam.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmesh.common.security.JwtService;
import com.flowmesh.iam.api.dto.LoginRequest;
import com.flowmesh.iam.api.dto.LogoutRequest;
import com.flowmesh.iam.api.dto.RefreshRequest;
import com.flowmesh.iam.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 验证登录、刷新令牌轮换和登出核心行为。
 *
 * <p>使用 V3 种子用户（applicant-a / password123）验证完整认证链路。</p>
 */
@AutoConfigureMockMvc
class AuthFlowIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    /**
     * 验证种子用户可登录，返回有效令牌对。
     *
     * @throws Exception 当请求执行失败时抛出
     */
    @Test
    void shouldLoginWithSeedUser() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new LoginRequest("tenant-a", "applicant-a", "password123")
                )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andReturn();

        String accessToken = objectMapper.readTree(result.getResponse().getContentAsString())
            .get("accessToken").asText();
        var principal = jwtService.parseAccessToken(accessToken);
        assertThat(principal.tenantId()).isEqualTo("tenant-a");
        assertThat(principal.username()).isEqualTo("applicant-a");
        assertThat(principal.roles()).contains("APPLICANT");
    }

    /**
     * 验证错误密码返回 401 INVALID_CREDENTIALS。
     *
     * @throws Exception 当请求执行失败时抛出
     */
    @Test
    void shouldRejectWrongPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new LoginRequest("tenant-a", "applicant-a", "wrong-password")
                )))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    /**
     * 验证刷新令牌轮换：新令牌生效，旧令牌不可复用。
     *
     * @throws Exception 当请求执行失败时抛出
     */
    @Test
    void shouldRotateRefreshToken() throws Exception {
        String oldRefresh = loginAndGetRefreshToken("tenant-a", "applicant-a", "password123");

        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshRequest(oldRefresh))))
                .andExpect(status().isOk())
                .andReturn();

        String newRefresh = objectMapper.readTree(refreshResult.getResponse().getContentAsString())
            .get("refreshToken").asText();
        assertThat(newRefresh).isNotEqualTo(oldRefresh);

        // 旧令牌复用被拒
        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshRequest(oldRefresh))))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 验证登出后刷新令牌不可用。
     *
     * @throws Exception 当请求执行失败时抛出
     */
    @Test
    void shouldRevokeTokenAfterLogout() throws Exception {
        String refreshToken = loginAndGetRefreshToken("tenant-a", "applicant-a", "password123");

        mockMvc.perform(post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LogoutRequest(refreshToken))))
                .andExpect(status().isNoContent());

        // 登出后刷新被拒
        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))))
                .andExpect(status().isUnauthorized());
    }

    private String loginAndGetRefreshToken(String tenantId, String username, String password)
        throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new LoginRequest(tenantId, username, password)
                )))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
            .get("refreshToken").asText();
    }
}

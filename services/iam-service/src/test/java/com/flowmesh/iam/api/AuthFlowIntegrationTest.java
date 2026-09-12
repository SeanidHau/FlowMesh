package com.flowmesh.iam.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmesh.common.security.JwtService;
import com.flowmesh.iam.api.dto.LoginRequest;
import com.flowmesh.iam.api.dto.LogoutRequest;
import com.flowmesh.iam.api.dto.RefreshRequest;
import com.flowmesh.iam.support.PostgresIntegrationTest;
import com.flowmesh.iam.rls.TenantRlsInitializer;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TenantRlsInitializer tenantRlsInitializer;

    @Autowired
    private PlatformTransactionManager transactionManager;

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
        String requestTraceId = "trace-login-failure-" + UUID.randomUUID();
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Trace-Id", requestTraceId)
                .content(objectMapper.writeValueAsString(
                    new LoginRequest("tenant-a", "applicant-a", "wrong-password")
                )))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn();

        String responseHeaderTraceId = result.getResponse().getHeader("X-Trace-Id");
        String responseTraceId = objectMapper.readTree(result.getResponse().getContentAsString())
            .get("traceId").asText();
        assertThat(responseHeaderTraceId).isEqualTo(requestTraceId);
        assertThat(responseTraceId).isEqualTo(requestTraceId);

        assertThat(auditCount(requestTraceId, "LOGIN", "FAILURE")).isEqualTo(1);
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
                .content(objectMapper.writeValueAsString(new RefreshRequest("tenant-a", oldRefresh))))
                .andExpect(status().isOk())
                .andReturn();

        String newRefresh = objectMapper.readTree(refreshResult.getResponse().getContentAsString())
            .get("refreshToken").asText();
        assertThat(newRefresh).isNotEqualTo(oldRefresh);

        // 旧令牌复用被拒
        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshRequest("tenant-a", oldRefresh))))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 验证错误租户不能使用另一个租户的 Refresh Token。
     *
     * @throws Exception 当请求执行失败时抛出
     */
    @Test
    void shouldRejectRefreshTokenFromAnotherTenant() throws Exception {
        String refreshToken = loginAndGetRefreshToken("tenant-a", "applicant-a", "password123");

        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshRequest("tenant-b", refreshToken))))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 验证登出后刷新令牌不可用。
     *
     * @throws Exception 当请求执行失败时抛出
     */
    @Test
    void shouldRevokeTokenAfterLogout() throws Exception {
        String loginTraceId = "trace-login-success-" + UUID.randomUUID();
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Trace-Id", loginTraceId)
                .content(objectMapper.writeValueAsString(
                    new LoginRequest("tenant-a", "applicant-a", "password123")
                )))
                .andExpect(status().isOk())
                .andReturn();
        String refreshToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
            .get("refreshToken").asText();
        assertThat(auditCount(loginTraceId, "LOGIN", "SUCCESS")).isEqualTo(1);

        String logoutTraceId = "trace-logout-" + UUID.randomUUID();

        mockMvc.perform(post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Trace-Id", logoutTraceId)
                .content(objectMapper.writeValueAsString(new LogoutRequest("tenant-a", refreshToken))))
                .andExpect(status().isNoContent());

        assertThat(auditCount(logoutTraceId, "LOGOUT", "SUCCESS")).isEqualTo(1);

        assertAuditMutationRejected(
            "UPDATE iam_audit_events SET result = 'FAILURE' WHERE trace_id = ?",
            logoutTraceId
        );
        assertAuditMutationRejected(
            "DELETE FROM iam_audit_events WHERE trace_id = ?",
            logoutTraceId
        );

        // 登出后刷新被拒
        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshRequest("tenant-a", refreshToken))))
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

    private int auditCount(String traceId, String action, String result) {
        Integer count = new TransactionTemplate(transactionManager).execute(status -> {
            tenantRlsInitializer.initialize("tenant-a");
            return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM iam_audit_events WHERE trace_id = ? AND action = ? AND result = ?",
                Integer.class,
                traceId,
                action,
                result
            );
        });
        return count == null ? 0 : count;
    }

    /**
     * 在正确租户上下文中验证审计表拒绝修改。
     *
     * @param sql 待执行的修改 SQL
     * @param traceId 审计记录链路标识
     */
    private void assertAuditMutationRejected(String sql, String traceId) {
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            tenantRlsInitializer.initialize("tenant-a");
            jdbcTemplate.update(sql, traceId);
        })).hasStackTraceContaining("append-only");
    }
}

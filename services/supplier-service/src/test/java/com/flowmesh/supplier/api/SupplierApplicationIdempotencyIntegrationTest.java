package com.flowmesh.supplier.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmesh.common.security.AuthPrincipal;
import com.flowmesh.common.security.JwtService;
import com.flowmesh.supplier.support.PostgresIntegrationTest;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 验证供应商申请创建与 Idempotency-Key 幂等。
 *
 * <p>覆盖创建/同键回放/异体冲突 409/缺键 400 四个核心场景。</p>
 */
@AutoConfigureMockMvc
class SupplierApplicationIdempotencyIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    private String applicantToken() {
        AuthPrincipal principal = new AuthPrincipal(
            UUID.randomUUID(), "applicant-a", "tenant-a", Set.of("APPLICANT")
        );
        return jwtService.issueAccessToken(principal, java.time.Instant.now());
    }

    /**
     * 验证合法请求创建成功，返回 201 和 SUBMITTED 状态。
     *
     * @throws Exception 当请求执行失败时抛出
     */
    @Test
    void shouldCreateApplication() throws Exception {
        mockMvc.perform(post("/api/v1/supplier-applications")
                .header("Authorization", "Bearer " + applicantToken())
                .header("Idempotency-Key", "create-key-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"supplierName\":\"测试供应商A\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.stateVersion").value(0))
                .andExpect(jsonPath("$.id").exists());
    }

    /**
     * 验证同键同请求体重复请求回放首次响应，applicationId 不变。
     *
     * @throws Exception 当请求执行失败时抛出
     */
    @Test
    void shouldReplayFirstResponseOnDuplicateKey() throws Exception {
        String token = applicantToken();
        String body = "{\"supplierName\":\"测试供应商B\"}";

        MvcResult first = mockMvc.perform(post("/api/v1/supplier-applications")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "replay-key-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult second = mockMvc.perform(post("/api/v1/supplier-applications")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "replay-key-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode firstJson = objectMapper.readTree(first.getResponse().getContentAsString());
        JsonNode secondJson = objectMapper.readTree(second.getResponse().getContentAsString());
        assertThat(secondJson.get("id").asText()).isEqualTo(firstJson.get("id").asText());
    }

    /**
     * 验证同键不同请求体返回 409 IDEMPOTENCY_KEY_CONFLICT。
     *
     * @throws Exception 当请求执行失败时抛出
     */
    @Test
    void shouldReturn409OnDifferentRequestBody() throws Exception {
        String token = applicantToken();

        mockMvc.perform(post("/api/v1/supplier-applications")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "conflict-key-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"supplierName\":\"供应商X\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/supplier-applications")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "conflict-key-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"supplierName\":\"供应商Y\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_CONFLICT"));
    }

    /**
     * 验证缺 Idempotency-Key 头返回 400。
     *
     * @throws Exception 当请求执行失败时抛出
     */
    @Test
    void shouldReturn400OnMissingIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/v1/supplier-applications")
                .header("Authorization", "Bearer " + applicantToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"supplierName\":\"测试供应商\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_IDEMPOTENCY_KEY"));
    }
}

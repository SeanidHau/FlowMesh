package com.flowmesh.supplier.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmesh.common.security.AuthPrincipal;
import com.flowmesh.common.security.JwtService;
import com.flowmesh.supplier.repository.OutboxEventRepository;
import com.flowmesh.supplier.application.SupplierSupplementService;
import com.flowmesh.supplier.application.WorkflowTaskCompletedService;
import com.flowmesh.supplier.support.PostgresIntegrationTest;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private SupplierSupplementService supplementService;

    @Autowired
    private WorkflowTaskCompletedService workflowTaskCompletedService;

    private static final UUID APPLICANT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private String applicantToken() {
        return applicantToken(APPLICANT_ID);
    }

    private String applicantToken(UUID userId) {
        AuthPrincipal principal = new AuthPrincipal(
            userId, "applicant-a", "tenant-a", Set.of("APPLICANT")
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
        MvcResult result = mockMvc.perform(post("/api/v1/supplier-applications")
                .header("Authorization", "Bearer " + applicantToken())
                .header("Idempotency-Key", "create-key-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"supplierName\":\"测试供应商A\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.stateVersion").value(0))
                .andExpect(jsonPath("$.id").exists())
                .andReturn();

        UUID applicationId = UUID.fromString(
            objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText()
        );
        var event = outboxEventRepository
            .findByAggregateIdAndTag(applicationId, "ApplicationSubmitted")
            .orElseThrow();
        assertThat(event.getTopic()).isEqualTo("supplier-events");
        assertThat(event.getTag()).isEqualTo("ApplicationSubmitted");
        assertThat(event.getPublishedAt()).isNull();
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
     * 验证同一幂等键的并发首请求都能得到确定响应，且数据库只创建一条申请和一条 Outbox 事件。
     *
     * <p>该场景覆盖数据库唯一约束仲裁后，失败事务通过独立事务读取获胜请求响应快照的路径。</p>
     *
     * @throws Exception 当并发请求或断言执行失败时抛出
     */
    @Test
    void shouldReplayWinnerForConcurrentSameKey() throws Exception {
        String token = applicantToken();
        String body = "{\"supplierName\":\"并发幂等供应商\"}";
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<MvcResult> first = submitCreate(executor, start, token, body);
            Future<MvcResult> second = submitCreate(executor, start, token, body);
            start.countDown();

            MvcResult firstResult = first.get(30, TimeUnit.SECONDS);
            MvcResult secondResult = second.get(30, TimeUnit.SECONDS);
            assertThat(firstResult.getResponse().getStatus()).isEqualTo(201);
            assertThat(secondResult.getResponse().getStatus()).isEqualTo(201);

            UUID firstApplicationId = applicationId(firstResult);
            UUID secondApplicationId = applicationId(secondResult);
            assertThat(secondApplicationId).isEqualTo(firstApplicationId);
            assertThat(outboxEventRepository.countByTenantIdAndAggregateIdAndTag(
                "tenant-a", firstApplicationId, "ApplicationSubmitted"
            )).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 提交一个由并发闸门控制的申请创建请求。
     *
     * @param executor 并发执行器
     * @param start 并发闸门
     * @param token Access Token
     * @param body 请求体
     * @return 异步请求结果
     */
    private Future<MvcResult> submitCreate(
        ExecutorService executor,
        CountDownLatch start,
        String token,
        String body
    ) {
        return executor.submit(() -> {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("并发测试未能在规定时间内开始");
            }
            return mockMvc.perform(post("/api/v1/supplier-applications")
                    .header("Authorization", "Bearer " + token)
                    .header("Idempotency-Key", "concurrent-key-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andReturn();
        });
    }

    /**
     * 从创建响应中读取申请标识。
     *
     * @param result MockMvc 响应
     * @return 申请标识
     * @throws Exception 当响应不是合法 JSON 时抛出
     */
    private UUID applicationId(MvcResult result) throws Exception {
        return UUID.fromString(objectMapper.readTree(
            result.getResponse().getContentAsString()
        ).get("id").asText());
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

    /**
     * 验证补件请求、申请人提交补件、幂等回放和补件 Outbox 在同一业务闭环内生效。
     *
     * @throws Exception 当请求执行失败时抛出
     */
    @Test
    void shouldSubmitSupplementWithReplayProtection() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/supplier-applications")
                .header("Authorization", "Bearer " + applicantToken(APPLICANT_ID))
                .header("Idempotency-Key", "supplement-create-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"supplierName\":\"补件供应商\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        UUID applicationId = UUID.fromString(
            objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText()
        );

        workflowTaskCompletedService.apply("""
            {
              "eventId":"%s","eventType":"WorkflowTaskCompleted","schemaVersion":1,
              "aggregateId":"%s","tenantId":"tenant-a","occurredAt":"2026-09-11T00:00:00Z",
              "traceId":"trace-workflow","payload":{"taskKey":"PURCHASER_REVIEW"}
            }
            """.formatted(UUID.randomUUID(), applicationId));

        supplementService.handleSupplementRequested("""
            {
              "eventId":"%s","eventType":"SupplementRequested","schemaVersion":1,
              "aggregateId":"%s","tenantId":"tenant-a","occurredAt":"2026-09-11T00:00:00Z",
              "traceId":"trace-supplement","payload":{"taskKey":"LEGAL_REVIEW",
              "comment":"请补充合规证明","roundNo":1}
            }
            """.formatted(UUID.randomUUID(), applicationId, applicationId));

        String supplementBody = "{\"comment\":\"已补充合规证明\"}";
        MvcResult first = mockMvc.perform(post("/api/v1/supplier-applications/{id}/supplements", applicationId)
                .header("Authorization", "Bearer " + applicantToken(APPLICANT_ID))
                .header("Idempotency-Key", "supplement-submit-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(supplementBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUBMITTED"))
            .andExpect(jsonPath("$.supplementCount").value(1))
            .andReturn();

        mockMvc.perform(post("/api/v1/supplier-applications/{id}/supplements", applicationId)
                .header("Authorization", "Bearer " + applicantToken(APPLICANT_ID))
                .header("Idempotency-Key", "supplement-submit-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(supplementBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.supplementCount").value(1))
            .andExpect(jsonPath("$.id").value(objectMapper.readTree(
                first.getResponse().getContentAsString()).get("id").asText()));

        assertThat(outboxEventRepository.findByAggregateIdAndTag(applicationId, "SupplementSubmitted"))
            .isPresent();
    }
}

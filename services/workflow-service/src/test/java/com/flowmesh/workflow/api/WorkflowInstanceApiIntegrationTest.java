package com.flowmesh.workflow.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowmesh.common.security.AuthPrincipal;
import com.flowmesh.common.security.JwtService;
import com.flowmesh.workflow.application.WorkflowEventProjectionService;
import com.flowmesh.workflow.repository.WorkflowOutboxEventRepository;
import com.flowmesh.workflow.support.PostgresIntegrationTest;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 验证 workflow 投影、租户隔离和角色推进 API 的最小闭环。
 */
@AutoConfigureMockMvc
class WorkflowInstanceApiIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private WorkflowEventProjectionService projectionService;

    @Autowired
    private WorkflowOutboxEventRepository outboxRepository;

    /**
     * 验证事件投影后可由正确租户查询和推进，错误租户不可见，错误角色被拒绝。
     *
     * @throws Exception 当请求执行失败时抛出
     */
    @Test
    void shouldQueryAndCompleteRoleBasedWorkflow() throws Exception {
        UUID applicationId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        String message = """
            {
              "eventId":"%s",
              "eventType":"ApplicationSubmitted",
              "schemaVersion":1,
              "aggregateId":"%s",
              "tenantId":"tenant-a",
              "occurredAt":"2026-08-31T00:00:00Z",
              "traceId":"trace-test",
              "payload":{
                "applicationId":"%s",
                "supplierName":"测试供应商",
                "applicantUserId":"00000000-0000-0000-0000-000000000001"
              }
            }
            """.formatted(eventId, applicationId, applicationId);
        projectionService.project(message);
        projectionService.project(message);

        mockMvc.perform(get("/api/v1/workflow-instances/{id}", applicationId)
                .header("Authorization", "Bearer " + token("tenant-a", Set.of("PURCHASER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentTask").value("PURCHASER_REVIEW"));

        mockMvc.perform(post("/api/v1/workflow-instances/{id}/tasks", applicationId)
                .header("Authorization", "Bearer " + token("tenant-a", Set.of("PURCHASER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"taskKey\":\"PURCHASER_REVIEW\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentTask").value("LEGAL_REVIEW"));

        assertThat(outboxRepository
            .findAllByAggregateIdAndTag(applicationId, "WorkflowTaskCompleted"))
            .hasSize(1);

        mockMvc.perform(get("/api/v1/workflow-instances/{id}", applicationId)
                .header("Authorization", "Bearer " + token("tenant-b", Set.of("PURCHASER"))))
            .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/workflow-instances/{id}/tasks", applicationId)
                .header("Authorization", "Bearer " + token("tenant-a", Set.of("PURCHASER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"taskKey\":\"LEGAL_REVIEW\"}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/workflow-instances/{id}/tasks", applicationId)
                .header("Authorization", "Bearer " + token("tenant-a", Set.of("LEGAL")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"taskKey\":\"LEGAL_REVIEW\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentTask").value("FINANCE_REVIEW"));

        mockMvc.perform(post("/api/v1/workflow-instances/{id}/tasks", applicationId)
                .header("Authorization", "Bearer " + token("tenant-a", Set.of("FINANCE")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"taskKey\":\"FINANCE_REVIEW\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentTask").value("OPERATIONS_ACTIVATION"));

        mockMvc.perform(post("/api/v1/workflow-instances/{id}/tasks", applicationId)
                .header("Authorization", "Bearer " + token("tenant-a", Set.of("OPERATIONS")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"taskKey\":\"OPERATIONS_ACTIVATION\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.currentTask").doesNotExist());

        assertThat(outboxRepository
            .findAllByAggregateIdAndTag(applicationId, "WorkflowTaskCompleted"))
            .hasSize(4);
    }

    private String token(String tenantId, Set<String> roles) {
        return jwtService.issueAccessToken(
            new AuthPrincipal(UUID.randomUUID(), "test-user", tenantId, roles),
            java.time.Instant.now()
        );
    }
}

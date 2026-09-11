package com.flowmesh.workflow.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmesh.common.messaging.EventEnvelopeValidator;
import com.flowmesh.workflow.domain.WorkflowInstance;
import com.flowmesh.workflow.domain.WorkflowTask;
import com.flowmesh.workflow.domain.WorkflowTaskRecord;
import com.flowmesh.workflow.repository.RiskEventInboxRepository;
import com.flowmesh.workflow.repository.WorkflowInstanceRepository;
import com.flowmesh.workflow.repository.WorkflowOutboxEventRepository;
import com.flowmesh.workflow.repository.WorkflowTaskRepository;
import com.flowmesh.workflow.domain.WorkflowOutboxEvent;
import java.time.Instant;
import com.flowmesh.workflow.rls.TenantRlsInitializer;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 处理 risk-service 返回的风控结果，并推进或终止流程。
 */
@Service
public class RiskCheckResultService {

    private final WorkflowInstanceRepository workflowInstanceRepository;
    private final RiskEventInboxRepository riskEventInboxRepository;
    private final TenantRlsInitializer tenantRlsInitializer;
    private final ObjectMapper objectMapper;
    private final WorkflowTaskRepository workflowTaskRepository;
    private final WorkflowOutboxEventRepository workflowOutboxEventRepository;

    /**
     * 创建风控结果服务。
     *
     * @param workflowInstanceRepository 流程实例仓储
     * @param riskEventInboxRepository 风控事件幂等仓储
     * @param tenantRlsInitializer 租户 RLS 初始化器
     * @param objectMapper JSON 解析器
     * @param workflowTaskRepository 审批任务仓储
     * @param workflowOutboxEventRepository workflow Outbox 仓储
     */
    public RiskCheckResultService(
        WorkflowInstanceRepository workflowInstanceRepository,
        RiskEventInboxRepository riskEventInboxRepository,
        TenantRlsInitializer tenantRlsInitializer,
        ObjectMapper objectMapper,
        WorkflowTaskRepository workflowTaskRepository,
        WorkflowOutboxEventRepository workflowOutboxEventRepository
    ) {
        this.workflowInstanceRepository = workflowInstanceRepository;
        this.riskEventInboxRepository = riskEventInboxRepository;
        this.tenantRlsInitializer = tenantRlsInitializer;
        this.objectMapper = objectMapper;
        this.workflowTaskRepository = workflowTaskRepository;
        this.workflowOutboxEventRepository = workflowOutboxEventRepository;
    }

    /**
     * 应用风控结果，重复事件只记录一次。
     *
     * @param message RocketMQ 事件
     */
    @Transactional
    public void apply(String message) {
        JsonNode event = readEvent(message);
        UUID eventId = EventEnvelopeValidator.requiredUuid(event, "eventId");
        UUID applicationId = EventEnvelopeValidator.requiredUuid(event, "aggregateId");
        String tenantId = EventEnvelopeValidator.requiredText(event, "tenantId");
        JsonNode payload = EventEnvelopeValidator.validate(event, "RiskCheckCompleted");
        UUID payloadApplicationId = EventEnvelopeValidator.requiredUuid(payload, "applicationId");
        String decision = EventEnvelopeValidator.requiredText(payload, "decision");
        if (!applicationId.equals(payloadApplicationId)) {
            throw new IllegalArgumentException("风控结果 aggregateId 与 payload.applicationId 不一致");
        }
        if (!java.util.Set.of("PASS", "REJECT").contains(decision)) {
            throw new IllegalArgumentException("风控结果 decision 无效");
        }

        tenantRlsInitializer.initialize(tenantId);
        if (riskEventInboxRepository.existsById(eventId)) {
            return;
        }
        WorkflowInstance instance = workflowInstanceRepository.findByApplicationId(applicationId)
            .orElseThrow(WorkflowInstanceNotFoundException::new);
        if ("PASS".equals(decision)) {
            instance.startProcurementReview();
        } else {
            instance.rejectRisk();
        }
        if (workflowInstanceRepository.updateState(instance) != 1) {
            throw new OptimisticLockingFailureException("风控结果更新时流程版本发生冲突");
        }
        if ("PASS".equals(decision)) {
            workflowTaskRepository.insert(new WorkflowTaskRecord(
                instance.getId(), tenantId, applicationId, WorkflowTask.PURCHASER_REVIEW
            ));
        } else {
            UUID rejectionEventId = UUID.randomUUID();
            workflowOutboxEventRepository.save(new WorkflowOutboxEvent(
                rejectionEventId,
                tenantId,
                applicationId,
                "workflow-events",
                "WorkflowRiskRejected",
                writeJson(new WorkflowRiskRejectedMessage(
                    rejectionEventId,
                    "WorkflowRiskRejected",
                    1,
                    tenantId,
                    applicationId,
                    Instant.now(),
                    event.path("traceId").asText(""),
                    new WorkflowRiskRejectedPayload(
                        instance.getApplicantUserId(),
                        payload.path("reason").asText("风控未通过")
                    )
                ))
            ));
        }
        riskEventInboxRepository.insert(eventId, tenantId, applicationId);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("风控拒绝事件序列化失败", exception);
        }
    }

    /**
     * 风控拒绝事件信封。
     */
    private record WorkflowRiskRejectedMessage(
        UUID eventId,
        String eventType,
        int schemaVersion,
        String tenantId,
        UUID aggregateId,
        Instant occurredAt,
        String traceId,
        WorkflowRiskRejectedPayload payload
    ) {
    }

    /**
     * 风控拒绝事件载荷。
     *
     * @param applicantUserId 申请人标识
     * @param reason 风控拒绝原因
     */
    private record WorkflowRiskRejectedPayload(UUID applicantUserId, String reason) {
    }

    private JsonNode readEvent(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            EventEnvelopeValidator.validate(event, "RiskCheckCompleted");
            return event;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("风控结果事件 JSON 无效", exception);
        }
    }
}

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
import com.flowmesh.workflow.repository.WorkflowTaskRepository;
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

    /**
     * 创建风控结果服务。
     *
     * @param workflowInstanceRepository 流程实例仓储
     * @param riskEventInboxRepository 风控事件幂等仓储
     * @param tenantRlsInitializer 租户 RLS 初始化器
     * @param objectMapper JSON 解析器
     */
    public RiskCheckResultService(
        WorkflowInstanceRepository workflowInstanceRepository,
        RiskEventInboxRepository riskEventInboxRepository,
        TenantRlsInitializer tenantRlsInitializer,
        ObjectMapper objectMapper,
        WorkflowTaskRepository workflowTaskRepository
    ) {
        this.workflowInstanceRepository = workflowInstanceRepository;
        this.riskEventInboxRepository = riskEventInboxRepository;
        this.tenantRlsInitializer = tenantRlsInitializer;
        this.objectMapper = objectMapper;
        this.workflowTaskRepository = workflowTaskRepository;
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
        }
        riskEventInboxRepository.insert(eventId, tenantId, applicationId);
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

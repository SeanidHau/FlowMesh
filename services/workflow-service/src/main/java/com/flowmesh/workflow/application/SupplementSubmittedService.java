package com.flowmesh.workflow.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmesh.common.messaging.EventEnvelopeValidator;
import com.flowmesh.workflow.domain.WorkflowInstance;
import com.flowmesh.workflow.domain.WorkflowSupplementEventInbox;
import com.flowmesh.workflow.domain.WorkflowTask;
import com.flowmesh.workflow.domain.WorkflowTaskRecord;
import com.flowmesh.workflow.repository.WorkflowInstanceRepository;
import com.flowmesh.workflow.repository.WorkflowSupplementEventInboxRepository;
import com.flowmesh.workflow.repository.WorkflowTaskRepository;
import com.flowmesh.workflow.rls.TenantRlsInitializer;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 处理申请人补件提交，并开启新的采购初审轮次。
 */
@Service
public class SupplementSubmittedService {

    private final WorkflowInstanceRepository instanceRepository;
    private final WorkflowTaskRepository taskRepository;
    private final WorkflowSupplementEventInboxRepository inboxRepository;
    private final TenantRlsInitializer tenantRlsInitializer;
    private final ObjectMapper objectMapper;

    /**
     * 创建补件提交服务。
     *
     * @param instanceRepository 流程实例仓储
     * @param taskRepository 任务仓储
     * @param inboxRepository 事件 Inbox
     * @param tenantRlsInitializer 租户 RLS 初始化器
     * @param objectMapper JSON 解析器
     */
    public SupplementSubmittedService(
        WorkflowInstanceRepository instanceRepository,
        WorkflowTaskRepository taskRepository,
        WorkflowSupplementEventInboxRepository inboxRepository,
        TenantRlsInitializer tenantRlsInitializer,
        ObjectMapper objectMapper
    ) {
        this.instanceRepository = instanceRepository;
        this.taskRepository = taskRepository;
        this.inboxRepository = inboxRepository;
        this.tenantRlsInitializer = tenantRlsInitializer;
        this.objectMapper = objectMapper;
    }

    /**
     * 应用补件提交事件，重复事件不会重复创建审批轮次。
     *
     * @param message RocketMQ 事件信封
     */
    @Transactional
    public void apply(String message) {
        JsonNode event = readEvent(message);
        UUID eventId = EventEnvelopeValidator.requiredUuid(event, "eventId");
        UUID applicationId = EventEnvelopeValidator.requiredUuid(event, "aggregateId");
        String tenantId = EventEnvelopeValidator.requiredText(event, "tenantId");
        JsonNode payload = EventEnvelopeValidator.validate(event, "SupplementSubmitted");
        UUID payloadApplicationId = EventEnvelopeValidator.requiredUuid(payload, "applicationId");
        if (!applicationId.equals(payloadApplicationId)) {
            throw new IllegalArgumentException("补件事件 aggregateId 与 payload.applicationId 不一致");
        }
        int roundNo = payload.path("roundNo").asInt(0);
        if (roundNo < 1) {
            throw new IllegalArgumentException("补件事件 roundNo 无效");
        }
        tenantRlsInitializer.initialize(tenantId);
        if (inboxRepository.existsById(eventId)) {
            return;
        }
        WorkflowInstance instance = instanceRepository.findByApplicationId(applicationId)
            .orElseThrow(WorkflowInstanceNotFoundException::new);
        instance.startSupplementReview();
        if (instance.getReviewRound() != roundNo + 1) {
            throw new IllegalArgumentException("补件事件轮次与流程轮次不一致");
        }
        if (instanceRepository.updateState(instance) != 1) {
            throw new OptimisticLockingFailureException("补件提交时流程版本发生冲突");
        }
        taskRepository.insert(new WorkflowTaskRecord(
            instance.getId(), tenantId, applicationId,
            WorkflowTask.PURCHASER_REVIEW, instance.getReviewRound()
        ));
        inboxRepository.save(new WorkflowSupplementEventInbox(eventId, tenantId, applicationId));
    }

    private JsonNode readEvent(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            EventEnvelopeValidator.validate(event, "SupplementSubmitted");
            return event;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("补件提交事件 JSON 无效", exception);
        }
    }
}

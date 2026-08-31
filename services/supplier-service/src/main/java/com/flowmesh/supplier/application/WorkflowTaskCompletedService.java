package com.flowmesh.supplier.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmesh.common.messaging.EventEnvelopeValidator;
import com.flowmesh.supplier.domain.OutboxEvent;
import com.flowmesh.supplier.domain.SupplierApplication;
import com.flowmesh.supplier.domain.WorkflowEventInbox;
import com.flowmesh.supplier.repository.OutboxEventRepository;
import com.flowmesh.supplier.repository.SupplierApplicationRepository;
import com.flowmesh.supplier.repository.WorkflowEventInboxRepository;
import com.flowmesh.supplier.rls.TenantRlsInitializer;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 处理 workflow-service 发来的审批完成事件。
 *
 * <p>申请状态、事件 Inbox 和启用通知 Outbox 在同一事务提交，保证重复消息不会重复推进业务状态。</p>
 */
@Service
public class WorkflowTaskCompletedService {

    private final SupplierApplicationRepository applicationRepository;
    private final WorkflowEventInboxRepository inboxRepository;
    private final OutboxEventRepository outboxRepository;
    private final TenantRlsInitializer tenantRlsInitializer;
    private final ObjectMapper objectMapper;

    /**
     * 创建 workflow 审批结果处理服务。
     *
     * @param applicationRepository 申请仓储
     * @param inboxRepository workflow 事件 Inbox 仓储
     * @param outboxRepository supplier 事件 Outbox 仓储
     * @param tenantRlsInitializer 租户 RLS 初始化器
     * @param objectMapper JSON 解析器
     */
    public WorkflowTaskCompletedService(
        SupplierApplicationRepository applicationRepository,
        WorkflowEventInboxRepository inboxRepository,
        OutboxEventRepository outboxRepository,
        TenantRlsInitializer tenantRlsInitializer,
        ObjectMapper objectMapper
    ) {
        this.applicationRepository = applicationRepository;
        this.inboxRepository = inboxRepository;
        this.outboxRepository = outboxRepository;
        this.tenantRlsInitializer = tenantRlsInitializer;
        this.objectMapper = objectMapper;
    }

    /**
     * 消费 workflow 审批完成事件。
     *
     * @param message RocketMQ 消息体
     */
    @Transactional
    public void apply(String message) {
        JsonNode event = readEvent(message);
        UUID eventId = EventEnvelopeValidator.requiredUuid(event, "eventId");
        UUID applicationId = EventEnvelopeValidator.requiredUuid(event, "aggregateId");
        String tenantId = EventEnvelopeValidator.requiredText(event, "tenantId");
        JsonNode payload = EventEnvelopeValidator.validate(event, "WorkflowTaskCompleted");
        String taskKey = EventEnvelopeValidator.requiredText(payload, "taskKey");
        if (!Set.of(
            "PURCHASER_REVIEW", "LEGAL_REVIEW", "FINANCE_REVIEW", "OPERATIONS_ACTIVATION"
        ).contains(taskKey)) {
            throw new IllegalArgumentException("事件 taskKey 无效");
        }

        tenantRlsInitializer.initializeTenant(tenantId);
        if (inboxRepository.existsById(eventId)) {
            return;
        }

        SupplierApplication application = applicationRepository.findById(applicationId)
            .orElseThrow(SupplierApplicationNotFoundException::new);
        application.applyWorkflowTask(taskKey);
        if (applicationRepository.updateState(application) != 1) {
            throw new OptimisticLockingFailureException("申请状态已被其他事务更新");
        }
        inboxRepository.save(new WorkflowEventInbox(eventId, tenantId, applicationId));

        if ("OPERATIONS_ACTIVATION".equals(taskKey)) {
            UUID activationEventId = UUID.randomUUID();
            outboxRepository.save(new OutboxEvent(
                activationEventId,
                tenantId,
                applicationId,
                "supplier-events",
                "SupplierActivated",
                writeJson(new SupplierActivatedMessage(
                    activationEventId,
                    "SupplierActivated",
                    1,
                    tenantId,
                    applicationId,
                    Instant.now(),
                    event.path("traceId").asText("")
                ))
            ));
        }
    }

    private JsonNode readEvent(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            EventEnvelopeValidator.validate(event, "WorkflowTaskCompleted");
            return event;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("workflow 事件 JSON 无效", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("供应商启用事件序列化失败", exception);
        }
    }

    /**
     * 供应商启用事件信封。
     *
     * @param eventId 事件标识
     * @param eventType 事件类型
     * @param schemaVersion 事件结构版本
     * @param tenantId 租户标识
     * @param aggregateId 申请标识
     * @param occurredAt 事件发生时间
     * @param traceId 链路追踪标识
     */
    private record SupplierActivatedMessage(
        UUID eventId,
        String eventType,
        int schemaVersion,
        String tenantId,
        UUID aggregateId,
        Instant occurredAt,
        String traceId
    ) {
    }
}

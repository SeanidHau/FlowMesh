package com.flowmesh.workflow.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmesh.common.messaging.EventEnvelopeValidator;
import com.flowmesh.workflow.domain.WorkflowInstance;
import com.flowmesh.workflow.repository.WorkflowInstanceRepository;
import com.flowmesh.workflow.repository.WorkflowOutboxEventRepository;
import com.flowmesh.workflow.domain.WorkflowOutboxEvent;
import java.time.Instant;
import com.flowmesh.workflow.rls.TenantRlsInitializer;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * 将供应商申请提交事件投影为流程实例。
 */
@Service
public class WorkflowEventProjectionService {

    private final WorkflowInstanceRepository workflowInstanceRepository;
    private final ObjectMapper objectMapper;
    private final TenantRlsInitializer tenantRlsInitializer;
    private final WorkflowOutboxEventRepository outboxRepository;
    private final Counter duplicateCounter;

    /**
     * 创建流程事件投影服务。
     *
     * @param workflowInstanceRepository 流程实例仓储
     * @param objectMapper JSON 解析器
     * @param tenantRlsInitializer 租户 RLS 初始化器
     */
    public WorkflowEventProjectionService(
        WorkflowInstanceRepository workflowInstanceRepository,
        ObjectMapper objectMapper,
        TenantRlsInitializer tenantRlsInitializer,
        WorkflowOutboxEventRepository outboxRepository,
        MeterRegistry meterRegistry
    ) {
        this.workflowInstanceRepository = workflowInstanceRepository;
        this.objectMapper = objectMapper;
        this.tenantRlsInitializer = tenantRlsInitializer;
        this.outboxRepository = outboxRepository;
        this.duplicateCounter = Counter.builder("flowmesh.messaging.duplicate")
            .tag("consumer", "workflow-application-submitted")
            .description("workflow 重复事件次数")
            .register(meterRegistry);
    }

    /**
     * 消费一条申请提交事件，重复事件直接忽略。
     *
     * @param message RocketMQ 消息体
     */
    @Transactional
    public void project(String message) {
        JsonNode event = readEvent(message);
        UUID eventId = EventEnvelopeValidator.requiredUuid(event, "eventId");
        UUID applicationId = EventEnvelopeValidator.requiredUuid(event, "aggregateId");
        String tenantId = EventEnvelopeValidator.requiredText(event, "tenantId");
        JsonNode payload = EventEnvelopeValidator.validate(event, "ApplicationSubmitted");
        UUID payloadApplicationId = EventEnvelopeValidator.requiredUuid(payload, "applicationId");
        EventEnvelopeValidator.requiredUuid(payload, "applicantUserId");
        String supplierName = EventEnvelopeValidator.requiredText(payload, "supplierName");
        if (!applicationId.equals(payloadApplicationId)) {
            throw new IllegalArgumentException("事件 aggregateId 与 payload.applicationId 不一致");
        }
        tenantRlsInitializer.initialize(tenantId);
        if (workflowInstanceRepository.existsBySourceEventId(eventId)) {
            duplicateCounter.increment();
            return;
        }

        workflowInstanceRepository.save(new WorkflowInstance(applicationId, eventId, tenantId));
        UUID riskEventId = UUID.randomUUID();
        outboxRepository.save(new WorkflowOutboxEvent(
            riskEventId,
            tenantId,
            applicationId,
            "risk-events",
            "RiskCheckRequested",
            writeJson(new RiskCheckRequestedMessage(
                riskEventId,
                "RiskCheckRequested",
                1,
                tenantId,
                applicationId,
                Instant.now(),
                event.path("traceId").asText(""),
                new RiskCheckRequestedPayload(applicationId, supplierName)
            ))
        ));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("风控事件序列化失败", exception);
        }
    }

    /**
     * 风控请求事件信封。
     *
     * @param eventId 事件标识
     * @param eventType 事件类型
     * @param schemaVersion 结构版本
     * @param tenantId 租户标识
     * @param aggregateId 申请标识
     * @param occurredAt 发生时间
     * @param traceId 链路标识
     * @param payload 风控请求载荷
     */
    private record RiskCheckRequestedMessage(
        UUID eventId,
        String eventType,
        int schemaVersion,
        String tenantId,
        UUID aggregateId,
        Instant occurredAt,
        String traceId,
        RiskCheckRequestedPayload payload
    ) {
    }

    /**
     * 风控请求载荷。
     *
     * @param applicationId 申请标识
     * @param supplierName 供应商名称
     */
    private record RiskCheckRequestedPayload(UUID applicationId, String supplierName) {
    }

    private JsonNode readEvent(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            EventEnvelopeValidator.validate(event, "ApplicationSubmitted");
            return event;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("事件 JSON 无效", exception);
        }
    }
}

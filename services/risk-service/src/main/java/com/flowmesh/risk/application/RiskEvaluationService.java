package com.flowmesh.risk.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmesh.common.messaging.EventEnvelopeValidator;
import com.flowmesh.risk.domain.RiskOutboxEvent;
import com.flowmesh.risk.domain.RiskResult;
import com.flowmesh.risk.repository.RiskOutboxRepository;
import com.flowmesh.risk.repository.RiskResultRepository;
import com.flowmesh.risk.rls.TenantRlsInitializer;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 处理风控请求并持久化可重试的风控结果事件。
 *
 * <p>这是可复现的模拟风控规则：供应商名称包含 {@code reject} 时拒绝，其余默认通过。
 * 规则故意集中在独立服务中，后续替换真实供应商征信接口不会影响流程服务。</p>
 */
@Service
public class RiskEvaluationService {

    private final RiskResultRepository resultRepository;
    private final RiskOutboxRepository outboxRepository;
    private final TenantRlsInitializer tenantRlsInitializer;
    private final ObjectMapper objectMapper;

    /**
     * 创建风控评估服务。
     *
     * @param resultRepository 风控结果仓储
     * @param outboxRepository 风控 Outbox 仓储
     * @param tenantRlsInitializer RLS 初始化器
     * @param objectMapper JSON 解析器
     */
    public RiskEvaluationService(
        RiskResultRepository resultRepository,
        RiskOutboxRepository outboxRepository,
        TenantRlsInitializer tenantRlsInitializer,
        ObjectMapper objectMapper
    ) {
        this.resultRepository = resultRepository;
        this.outboxRepository = outboxRepository;
        this.tenantRlsInitializer = tenantRlsInitializer;
        this.objectMapper = objectMapper;
    }

    /**
     * 处理一条风控请求；同一申请已有结果时直接幂等返回。
     *
     * @param message 风控请求事件
     */
    @Transactional
    public void evaluate(String message) {
        JsonNode event = readEvent(message);
        UUID eventId = EventEnvelopeValidator.requiredUuid(event, "eventId");
        UUID applicationId = EventEnvelopeValidator.requiredUuid(event, "aggregateId");
        String tenantId = EventEnvelopeValidator.requiredText(event, "tenantId");
        JsonNode payload = EventEnvelopeValidator.validate(event, "RiskCheckRequested");
        UUID payloadApplicationId = EventEnvelopeValidator.requiredUuid(payload, "applicationId");
        String supplierName = EventEnvelopeValidator.requiredText(payload, "supplierName");
        if (!applicationId.equals(payloadApplicationId)) {
            throw new IllegalArgumentException("风控请求 aggregateId 与 payload.applicationId 不一致");
        }

        tenantRlsInitializer.initialize(tenantId);
        if (resultRepository.findByApplicationId(applicationId).isPresent()) {
            return;
        }

        RiskResult.Decision decision = supplierName.toLowerCase(Locale.ROOT).contains("reject")
            ? RiskResult.Decision.REJECT
            : RiskResult.Decision.PASS;
        String reason = decision == RiskResult.Decision.REJECT
            ? "命中模拟风险拒绝规则"
            : "模拟风险校验通过";
        RiskResult result = RiskResult.create(tenantId, applicationId, decision, reason);
        resultRepository.insert(result);

        UUID resultEventId = UUID.randomUUID();
        outboxRepository.insert(new RiskOutboxEvent(
            resultEventId,
            tenantId,
            applicationId,
            "risk-events",
            "RiskCheckCompleted",
            writeJson(new RiskCheckCompletedMessage(
                resultEventId,
                "RiskCheckCompleted",
                1,
                tenantId,
                applicationId,
                Instant.now(),
                event.path("traceId").asText(""),
                new RiskCheckCompletedPayload(applicationId, decision.name(), reason, eventId)
            ))
        ));
    }

    private JsonNode readEvent(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            EventEnvelopeValidator.validate(event, "RiskCheckRequested");
            return event;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("风控请求事件 JSON 无效", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("风控结果事件序列化失败", exception);
        }
    }

    /**
     * 风控结果事件信封。
     *
     * @param eventId 事件标识
     * @param eventType 事件类型
     * @param schemaVersion 结构版本
     * @param tenantId 租户标识
     * @param aggregateId 申请标识
     * @param occurredAt 发生时间
     * @param traceId 链路标识
     * @param payload 结果载荷
     */
    private record RiskCheckCompletedMessage(
        UUID eventId,
        String eventType,
        int schemaVersion,
        String tenantId,
        UUID aggregateId,
        Instant occurredAt,
        String traceId,
        RiskCheckCompletedPayload payload
    ) {
    }

    /**
     * 风控结果载荷。
     *
     * @param applicationId 申请标识
     * @param decision 决策
     * @param reason 原因
     * @param requestedEventId 对应的请求事件
     */
    private record RiskCheckCompletedPayload(
        UUID applicationId,
        String decision,
        String reason,
        UUID requestedEventId
    ) {
    }
}

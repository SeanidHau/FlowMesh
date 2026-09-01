package com.flowmesh.workflow.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmesh.common.security.AuthPrincipal;
import com.flowmesh.workflow.api.dto.DeadLetterEventResponse;
import com.flowmesh.workflow.api.dto.ReplayOutboxResponse;
import com.flowmesh.workflow.domain.OutboxReplayAudit;
import com.flowmesh.workflow.domain.WorkflowOutboxEvent;
import com.flowmesh.workflow.repository.OutboxReplayAuditRepository;
import com.flowmesh.workflow.repository.WorkflowOutboxEventRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 提供 workflow Outbox 的死信查询和受控重放能力。
 */
@Service
public class OutboxOperationsService {

    private final WorkflowOutboxEventRepository outboxRepository;
    private final OutboxReplayAuditRepository auditRepository;
    private final ObjectMapper objectMapper;

    /**
     * 创建 Outbox 运维服务。
     *
     * @param outboxRepository Outbox 仓储
     * @param auditRepository 重放审计仓储
     * @param objectMapper JSON 处理器
     */
    public OutboxOperationsService(
        WorkflowOutboxEventRepository outboxRepository,
        OutboxReplayAuditRepository auditRepository,
        ObjectMapper objectMapper
    ) {
        this.outboxRepository = outboxRepository;
        this.auditRepository = auditRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 查询当前租户的死信事件。
     *
     * @param principal 当前操作者
     * @param eventType 事件类型，可为空
     * @param aggregateId 聚合标识，可为空
     * @return 死信列表
     */
    @Transactional(readOnly = true)
    public List<DeadLetterEventResponse> listDeadLetters(
        AuthPrincipal principal,
        String eventType,
        UUID aggregateId
    ) {
        return outboxRepository.findDeadLettered(principal.tenantId(), eventType, aggregateId, 100)
            .stream().map(DeadLetterEventResponse::from).toList();
    }

    /**
     * 创建一条新的待发布事件并记录重放审计。
     *
     * @param principal 当前操作者
     * @param eventId 原死信标识
     * @param reason 重放原因
     * @param traceId 链路标识
     * @return 重放结果
     */
    @Transactional
    public ReplayOutboxResponse replay(
        AuthPrincipal principal,
        UUID eventId,
        String reason,
        String traceId
    ) {
        WorkflowOutboxEvent original = outboxRepository.findDeadLetteredById(principal.tenantId(), eventId)
            .orElseThrow(DeadLetterEventNotFoundException::new);
        UUID replayId = UUID.randomUUID();
        String payload = rewriteEventIds(original.getPayload(), replayId, original.getId());
        outboxRepository.save(WorkflowOutboxEvent.replay(
            replayId, original.getId(), original.getTenantId(), original.getAggregateId(),
            original.getTopic(), original.getTag(), payload
        ));
        auditRepository.insert(new OutboxReplayAudit(
            principal.tenantId(), principal.userId(), original.getId(), replayId, reason, traceId
        ));
        return new ReplayOutboxResponse(original.getId(), replayId, "PENDING", java.time.Instant.now());
    }

    private String rewriteEventIds(String rawPayload, UUID replayId, UUID originalEventId) {
        try {
            JsonNode event = objectMapper.readTree(rawPayload);
            if (!event.isObject()) {
                throw new InvalidReplayException();
            }
            ((com.fasterxml.jackson.databind.node.ObjectNode) event)
                .put("eventId", replayId.toString())
                .put("originalEventId", originalEventId.toString());
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException | ClassCastException exception) {
            throw new InvalidReplayException();
        }
    }
}

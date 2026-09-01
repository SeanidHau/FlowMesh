package com.flowmesh.workflow.api.dto;

import com.flowmesh.workflow.domain.WorkflowOutboxEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * 面向运维人员展示的 workflow 死信事件。
 *
 * @param eventId 事件标识
 * @param originalEventId 原始事件标识
 * @param tenantId 租户标识
 * @param aggregateId 聚合标识
 * @param eventType 事件类型
 * @param topic RocketMQ Topic
 * @param attemptCount 尝试次数
 * @param lastError 最近错误
 * @param deadLetteredAt 进入死信时间
 * @param payload 原始事件信封
 */
public record DeadLetterEventResponse(
    UUID eventId,
    UUID originalEventId,
    String tenantId,
    UUID aggregateId,
    String eventType,
    String topic,
    int attemptCount,
    String lastError,
    Instant deadLetteredAt,
    String payload
) {

    /**
     * 将持久化事件转换为 API 响应。
     *
     * @param event 死信事件
     * @return API 响应
     */
    public static DeadLetterEventResponse from(WorkflowOutboxEvent event) {
        return new DeadLetterEventResponse(
            event.getId(), event.getOriginalEventId(), event.getTenantId(), event.getAggregateId(),
            event.getTag(), event.getTopic(), event.getAttemptCount(), event.getLastError(),
            event.getDeadLetteredAt(), event.getPayload()
        );
    }
}

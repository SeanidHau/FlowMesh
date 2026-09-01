package com.flowmesh.workflow.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * 死信重放响应。
 *
 * @param originalEventId 原始死信标识
 * @param replayEventId 新事件标识
 * @param status 新事件状态
 * @param createdAt 创建时间
 */
public record ReplayOutboxResponse(
    UUID originalEventId,
    UUID replayEventId,
    String status,
    Instant createdAt
) {
}

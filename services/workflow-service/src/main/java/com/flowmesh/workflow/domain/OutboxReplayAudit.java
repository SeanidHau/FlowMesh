package com.flowmesh.workflow.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 记录 workflow Outbox 死信重放操作。
 */
public class OutboxReplayAudit {

    private final UUID id;
    private final String tenantId;
    private final UUID operatorUserId;
    private final UUID originalEventId;
    private final UUID replayEventId;
    private final String reason;
    private final String sourceStatus;
    private final String replayStatus;
    private final String traceId;
    private final Instant createdAt;

    /**
     * 创建重放审计记录。
     *
     * @param tenantId 租户标识
     * @param operatorUserId 操作者标识
     * @param originalEventId 原始死信标识
     * @param replayEventId 新事件标识
     * @param reason 重放原因
     * @param traceId 链路标识
     */
    public OutboxReplayAudit(
        String tenantId,
        UUID operatorUserId,
        UUID originalEventId,
        UUID replayEventId,
        String reason,
        String traceId
    ) {
        this.id = UUID.randomUUID();
        this.tenantId = Objects.requireNonNull(tenantId);
        this.operatorUserId = Objects.requireNonNull(operatorUserId);
        this.originalEventId = Objects.requireNonNull(originalEventId);
        this.replayEventId = Objects.requireNonNull(replayEventId);
        this.reason = Objects.requireNonNull(reason);
        this.sourceStatus = "DEAD_LETTERED";
        this.replayStatus = "PENDING";
        this.traceId = traceId == null ? "" : traceId;
        this.createdAt = Instant.now();
    }

    /**
     * 获取审计记录标识。
     *
     * @return 审计记录标识
     */
    public UUID getId() { return id; }

    /**
     * 获取租户标识。
     *
     * @return 租户标识
     */
    public String getTenantId() { return tenantId; }

    /**
     * 获取操作者标识。
     *
     * @return 操作者标识
     */
    public UUID getOperatorUserId() { return operatorUserId; }

    /**
     * 获取原始事件标识。
     *
     * @return 原始事件标识
     */
    public UUID getOriginalEventId() { return originalEventId; }

    /**
     * 获取重放事件标识。
     *
     * @return 重放事件标识
     */
    public UUID getReplayEventId() { return replayEventId; }

    /**
     * 获取重放原因。
     *
     * @return 重放原因
     */
    public String getReason() { return reason; }

    /**
     * 获取原事件状态。
     *
     * @return 原事件状态
     */
    public String getSourceStatus() { return sourceStatus; }

    /**
     * 获取重放事件初始状态。
     *
     * @return 重放事件状态
     */
    public String getReplayStatus() { return replayStatus; }

    /**
     * 获取链路标识。
     *
     * @return 链路标识
     */
    public String getTraceId() { return traceId; }

    /**
     * 获取审计时间。
     *
     * @return 创建时间
     */
    public Instant getCreatedAt() { return createdAt; }
}

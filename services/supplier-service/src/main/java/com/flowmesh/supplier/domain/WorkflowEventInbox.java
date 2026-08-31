package com.flowmesh.supplier.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * supplier-service 已处理的 workflow 事件记录。
 *
 * <p>事件 ID 作为主键，使重复投递不会重复推进申请状态。</p>
 */
public class WorkflowEventInbox {

    private UUID eventId;

    private String tenantId;

    private UUID aggregateId;

    private Instant createdAt;

    /**
     * 供 MyBatis 重建持久化对象状态使用。
     */
    protected WorkflowEventInbox() {
    }

    /**
     * 创建 workflow 事件幂等记录。
     *
     * @param eventId 事件标识
     * @param tenantId 租户标识
     * @param aggregateId 申请标识
     */
    public WorkflowEventInbox(UUID eventId, String tenantId, UUID aggregateId) {
        this.eventId = Objects.requireNonNull(eventId);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.aggregateId = Objects.requireNonNull(aggregateId);
        this.createdAt = Instant.now();
    }

    /**
     * 获取事件标识。
     *
     * @return 事件标识
     */
    public UUID getEventId() {
        return eventId;
    }

}

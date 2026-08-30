package com.flowmesh.supplier.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * supplier-service 已处理的 workflow 事件记录。
 *
 * <p>事件 ID 作为主键，使重复投递不会重复推进申请状态。</p>
 */
@Entity
@Table(name = "supplier_workflow_event_inbox")
public class WorkflowEventInbox {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "tenant_id", nullable = false, updatable = false, length = 64)
    private String tenantId;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * 供 JPA 重建实体状态使用。
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
    }

    /**
     * 获取事件标识。
     *
     * @return 事件标识
     */
    public UUID getEventId() {
        return eventId;
    }

    @PrePersist
    private void onCreate() {
        createdAt = Instant.now();
    }
}

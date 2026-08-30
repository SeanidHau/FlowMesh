package com.flowmesh.workflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * workflow-service 待投递的跨服务事件。
 *
 * <p>审批状态和 Outbox 在同一事务写入，确保状态推进后事件最终可投递。</p>
 */
@Entity
@Table(name = "workflow_outbox_events")
public class WorkflowOutboxEvent {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false, length = 64)
    private String tenantId;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    @Column(nullable = false, updatable = false, length = 128)
    private String topic;

    @Column(nullable = false, updatable = false, length = 128)
    private String tag;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * 供 JPA 重建实体状态使用。
     */
    protected WorkflowOutboxEvent() {
    }

    /**
     * 创建 workflow 待投递事件。
     *
     * @param id 事件标识
     * @param tenantId 租户标识
     * @param aggregateId 申请标识
     * @param topic RocketMQ Topic
     * @param tag RocketMQ Tag
     * @param payload 事件信封 JSON
     */
    public WorkflowOutboxEvent(
        UUID id,
        String tenantId,
        UUID aggregateId,
        String topic,
        String tag,
        String payload
    ) {
        this.id = Objects.requireNonNull(id);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.aggregateId = Objects.requireNonNull(aggregateId);
        this.topic = Objects.requireNonNull(topic);
        this.tag = Objects.requireNonNull(tag);
        this.payload = Objects.requireNonNull(payload);
    }

    /**
     * 获取事件标识。
     *
     * @return 事件标识
     */
    public UUID getId() {
        return id;
    }

    /**
     * 获取申请标识。
     *
     * @return 申请标识
     */
    public UUID getAggregateId() {
        return aggregateId;
    }

    /**
     * 获取 RocketMQ Topic。
     *
     * @return Topic
     */
    public String getTopic() {
        return topic;
    }

    /**
     * 获取 RocketMQ Tag。
     *
     * @return Tag
     */
    public String getTag() {
        return tag;
    }

    /**
     * 获取事件信封 JSON。
     *
     * @return JSON 载荷
     */
    public String getPayload() {
        return payload;
    }

    /**
     * 获取已尝试投递次数。
     *
     * @return 尝试次数
     */
    public int getAttemptCount() {
        return attemptCount;
    }

    /**
     * 标记事件已被 RocketMQ 接收。
     *
     * @param publishedAt Broker 返回成功的时间
     */
    public void markPublished(Instant publishedAt) {
        this.publishedAt = Objects.requireNonNull(publishedAt);
        this.lastError = null;
    }

    /**
     * 记录一次发布失败，保留事件等待下一轮重试。
     *
     * @param error 错误信息
     */
    public void recordFailure(String error) {
        attemptCount++;
        lastError = error == null ? "unknown" : error.substring(0, Math.min(error.length(), 1000));
    }

    @PrePersist
    private void onCreate() {
        createdAt = Instant.now();
    }
}

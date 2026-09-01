package com.flowmesh.workflow.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * workflow-service 待投递的跨服务事件。
 *
 * <p>审批状态和 Outbox 在同一事务写入，确保状态推进后事件最终可投递。</p>
 */
public class WorkflowOutboxEvent {

    private UUID id;

    private String tenantId;

    private UUID aggregateId;

    private String topic;

    private String tag;

    private String payload;

    private int attemptCount;

    private String lastError;

    private Instant publishedAt;

    private Instant nextAttemptAt;

    private Instant claimedUntil;

    private UUID claimToken;

    private Instant deadLetteredAt;

    private UUID originalEventId;

    private Instant createdAt;

    /**
     * 供 MyBatis 重建持久化对象状态使用。
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
        this.createdAt = Instant.now();
        this.nextAttemptAt = this.createdAt;
    }

    /**
     * 创建一条由死信重放产生的新 workflow Outbox 事件。
     *
     * @param replayId 重放事件的新标识
     * @param originalEventId 原始死信事件标识
     * @param tenantId 租户标识
     * @param aggregateId 业务聚合标识
     * @param topic RocketMQ Topic
     * @param tag RocketMQ Tag
     * @param payload 已替换新事件标识的事件信封
     * @return 待重放事件
     */
    public static WorkflowOutboxEvent replay(
        UUID replayId,
        UUID originalEventId,
        String tenantId,
        UUID aggregateId,
        String topic,
        String tag,
        String payload
    ) {
        WorkflowOutboxEvent event = new WorkflowOutboxEvent(replayId, tenantId, aggregateId, topic, tag, payload);
        event.originalEventId = Objects.requireNonNull(originalEventId);
        return event;
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
     * 获取租户标识。
     *
     * @return 租户标识
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * 获取死信时间。
     *
     * @return 死信时间；未进入死信时为 {@code null}
     */
    public Instant getDeadLetteredAt() {
        return deadLetteredAt;
    }

    /**
     * 获取原始死信事件标识。
     *
     * @return 原始事件标识；普通事件为 {@code null}
     */
    public UUID getOriginalEventId() {
        return originalEventId;
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
     * 获取最近一次投递错误。
     *
     * @return 错误信息；从未失败时为 {@code null}
     */
    public String getLastError() {
        return lastError;
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
     * 获取当前发布认领令牌。
     *
     * @return 认领令牌
     */
    public UUID getClaimToken() {
        return claimToken;
    }

    /**
     * 认领事件，认领事务提交后才允许网络发送。
     *
     * @param token 本次发布器实例的认领令牌
     * @param leaseUntil 认领租约到期时间
     */
    public void claim(UUID token, Instant leaseUntil) {
        this.claimToken = Objects.requireNonNull(token);
        this.claimedUntil = Objects.requireNonNull(leaseUntil);
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

}

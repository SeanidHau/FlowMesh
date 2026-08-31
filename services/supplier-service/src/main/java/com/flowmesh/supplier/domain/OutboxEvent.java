package com.flowmesh.supplier.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 记录待投递的领域事件。
 *
 * <p>该记录与业务状态在同一数据库事务中写入，RocketMQ 发布成功后再标记
 * {@code publishedAt}，从而允许发布器在服务重启后继续投递。</p>
 */
public class OutboxEvent {

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

    private Instant createdAt;

    /**
     * 供 MyBatis 重建持久化对象状态使用。
     */
    protected OutboxEvent() {
    }

    /**
     * 创建待投递事件。
     *
     * @param id 事件唯一标识
     * @param tenantId 租户标识
     * @param aggregateId 业务聚合标识
     * @param topic RocketMQ Topic
     * @param tag RocketMQ Tag
     * @param payload 事件信封 JSON
     */
    public OutboxEvent(
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
     * 获取事件唯一标识。
     *
     * @return 事件 ID
     */
    public UUID getId() {
        return id;
    }

    /**
     * 获取租户标识。
     *
     * @return 租户 ID
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * 获取聚合标识。
     *
     * @return 聚合 ID
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
     * 获取成功投递时间。
     *
     * @return 投递时间；未成功时为 {@code null}
     */
    public Instant getPublishedAt() {
        return publishedAt;
    }

    /**
     * 获取下一次允许投递的时间。
     *
     * @return 下一次投递时间
     */
    public Instant getNextAttemptAt() {
        return nextAttemptAt;
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
     * 标记事件已被 RocketMQ 接收。
     *
     * @param occurredAt Broker 返回成功的时间
     */
    public void markPublished(Instant occurredAt) {
        this.publishedAt = Objects.requireNonNull(occurredAt);
        this.lastError = null;
    }

    /**
     * 记录一次发布失败，保留事件等待下一轮重试。
     *
     * @param error 错误信息
     */
    public void recordFailure(String error) {
        this.attemptCount++;
        this.lastError = error == null ? "unknown" : error.substring(0, Math.min(error.length(), 1000));
    }

}

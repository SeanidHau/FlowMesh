package com.flowmesh.risk.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 风控结果 Outbox 事件。
 */
public class RiskOutboxEvent {

    private UUID id;
    private String tenantId;
    private UUID aggregateId;
    private String topic;
    private String tag;
    private String payload;
    private int attemptCount;
    private String lastError;
    private Instant nextAttemptAt;
    private Instant claimedUntil;
    private UUID claimToken;
    private Instant publishedAt;
    private Instant deadLetteredAt;
    private Instant createdAt;

    /**
     * 供 MyBatis 重建查询结果。
     */
    protected RiskOutboxEvent() {
    }

    /**
     * 创建待发布事件。
     *
     * @param id 事件标识
     * @param tenantId 租户标识
     * @param aggregateId 申请标识
     * @param topic Topic
     * @param tag Tag
     * @param payload JSON 载荷
     */
    public RiskOutboxEvent(
        UUID id,
        String tenantId,
        UUID aggregateId,
        String topic,
        String tag,
        String payload
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.aggregateId = aggregateId;
        this.topic = topic;
        this.tag = tag;
        this.payload = payload;
        this.attemptCount = 0;
        this.nextAttemptAt = Instant.now();
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public UUID getAggregateId() { return aggregateId; }
    public String getTopic() { return topic; }
    public String getTag() { return tag; }
    public String getPayload() { return payload; }
    public int getAttemptCount() { return attemptCount; }
    public String getLastError() { return lastError; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getClaimedUntil() { return claimedUntil; }
    public UUID getClaimToken() { return claimToken; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getDeadLetteredAt() { return deadLetteredAt; }
    public Instant getCreatedAt() { return createdAt; }

    /**
     * 设置事件标识，供 MyBatis 映射查询结果。
     *
     * @param id 事件标识
     */
    public void setId(UUID id) { this.id = id; }

    /**
     * 设置租户标识，供 MyBatis 映射查询结果。
     *
     * @param tenantId 租户标识
     */
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    /**
     * 设置聚合标识，供 MyBatis 映射查询结果。
     *
     * @param aggregateId 聚合标识
     */
    public void setAggregateId(UUID aggregateId) { this.aggregateId = aggregateId; }

    /**
     * 设置 Topic，供 MyBatis 映射查询结果。
     *
     * @param topic Topic
     */
    public void setTopic(String topic) { this.topic = topic; }

    /**
     * 设置 Tag，供 MyBatis 映射查询结果。
     *
     * @param tag Tag
     */
    public void setTag(String tag) { this.tag = tag; }

    /**
     * 设置消息载荷，供 MyBatis 映射查询结果。
     *
     * @param payload JSON 载荷
     */
    public void setPayload(String payload) { this.payload = payload; }

    /**
     * 设置发送尝试次数，供 MyBatis 映射查询结果。
     *
     * @param attemptCount 尝试次数
     */
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }

    /**
     * 设置最近一次错误，供 MyBatis 映射查询结果。
     *
     * @param lastError 错误摘要
     */
    public void setLastError(String lastError) { this.lastError = lastError; }

    /**
     * 设置下次尝试时间，供 MyBatis 映射查询结果。
     *
     * @param nextAttemptAt 下次尝试时间
     */
    public void setNextAttemptAt(Instant nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }

    /**
     * 设置认领租约截止时间，供 MyBatis 映射查询结果。
     *
     * @param claimedUntil 租约截止时间
     */
    public void setClaimedUntil(Instant claimedUntil) { this.claimedUntil = claimedUntil; }

    /**
     * 设置认领令牌，供 MyBatis 映射查询结果。
     *
     * @param claimToken 认领令牌
     */
    public void setClaimToken(UUID claimToken) { this.claimToken = claimToken; }

    /**
     * 设置发布时间，供 MyBatis 映射查询结果。
     *
     * @param publishedAt 发布时间
     */
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }

    /**
     * 设置死信时间，供 MyBatis 映射查询结果。
     *
     * @param deadLetteredAt 死信时间
     */
    public void setDeadLetteredAt(Instant deadLetteredAt) { this.deadLetteredAt = deadLetteredAt; }

    /**
     * 设置创建时间，供 MyBatis 映射查询结果。
     *
     * @param createdAt 创建时间
     */
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

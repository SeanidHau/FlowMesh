package com.flowmesh.risk.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 风控服务的最终结果记录。
 *
 * @param tenantId 租户标识
 * @param applicationId 申请标识
 * @param decision 风控决策
 * @param reason 决策原因
 */
public class RiskResult {

    private UUID id;
    private String tenantId;
    private UUID applicationId;
    private Decision decision;
    private String reason;
    private Instant createdAt;

    /**
     * 供 MyBatis 重建对象使用。
     */
    protected RiskResult() {
    }

    /**
     * 创建风控结果。
     *
     * @param tenantId 租户标识
     * @param applicationId 申请标识
     * @param decision 决策
     * @param reason 原因
     * @return 风控结果
     */
    public static RiskResult create(
        String tenantId,
        UUID applicationId,
        Decision decision,
        String reason
    ) {
        return new RiskResult(
            UUID.randomUUID(),
            Objects.requireNonNull(tenantId),
            Objects.requireNonNull(applicationId),
            Objects.requireNonNull(decision),
            Objects.requireNonNull(reason),
            Instant.now()
        );
    }

    private RiskResult(
        UUID id,
        String tenantId,
        UUID applicationId,
        Decision decision,
        String reason,
        Instant createdAt
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.applicationId = applicationId;
        this.decision = decision;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public UUID getApplicationId() { return applicationId; }
    public Decision getDecision() { return decision; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }

    /**
     * 设置结果标识，供 MyBatis 映射查询结果。
     *
     * @param id 结果标识
     */
    public void setId(UUID id) { this.id = id; }

    /**
     * 设置租户标识，供 MyBatis 映射查询结果。
     *
     * @param tenantId 租户标识
     */
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    /**
     * 设置申请标识，供 MyBatis 映射查询结果。
     *
     * @param applicationId 申请标识
     */
    public void setApplicationId(UUID applicationId) { this.applicationId = applicationId; }

    /**
     * 设置风控决策，供 MyBatis 映射查询结果。
     *
     * @param decision 风控决策
     */
    public void setDecision(Decision decision) { this.decision = decision; }

    /**
     * 设置决策原因，供 MyBatis 映射查询结果。
     *
     * @param reason 决策原因
     */
    public void setReason(String reason) { this.reason = reason; }

    /**
     * 设置创建时间，供 MyBatis 映射查询结果。
     *
     * @param createdAt 创建时间
     */
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    /**
     * 风控决策。
     */
    public enum Decision {
        PASS,
        REJECT
    }
}

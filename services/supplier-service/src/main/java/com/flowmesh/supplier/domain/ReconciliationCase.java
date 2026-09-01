package com.flowmesh.supplier.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 保存跨服务状态对账发现的待处置差异。
 */
public class ReconciliationCase {

    private final UUID id;
    private final String tenantId;
    private final UUID applicationId;
    private final String discrepancyType;
    private final String details;
    private final Instant detectedAt;

    /**
     * 创建一条打开状态的对账记录。
     *
     * @param tenantId 租户标识
     * @param applicationId 申请标识
     * @param discrepancyType 差异类型
     * @param details 差异详情
     */
    public ReconciliationCase(
        String tenantId,
        UUID applicationId,
        String discrepancyType,
        String details
    ) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.applicationId = applicationId;
        this.discrepancyType = discrepancyType;
        this.details = details;
        this.detectedAt = Instant.now();
    }

    /**
     * 获取对账记录标识。
     *
     * @return 对账记录标识
     */
    public UUID getId() { return id; }

    /**
     * 获取租户标识。
     *
     * @return 租户标识
     */
    public String getTenantId() { return tenantId; }

    /**
     * 获取申请标识。
     *
     * @return 申请标识
     */
    public UUID getApplicationId() { return applicationId; }

    /**
     * 获取差异类型。
     *
     * @return 差异类型
     */
    public String getDiscrepancyType() { return discrepancyType; }

    /**
     * 获取差异详情。
     *
     * @return 差异详情
     */
    public String getDetails() { return details; }

    /**
     * 获取发现时间。
     *
     * @return 发现时间
     */
    public Instant getDetectedAt() { return detectedAt; }
}

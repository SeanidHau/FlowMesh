package com.flowmesh.supplier.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 一轮补件提交的不可变历史记录。
 */
public class SupplierSupplement {

    private final UUID id;
    private final String tenantId;
    private final UUID applicationId;
    private final int roundNo;
    private final UUID submittedBy;
    private final String comment;
    private final Instant createdAt;

    /**
     * 创建补件历史记录。
     *
     * @param tenantId 租户标识
     * @param applicationId 申请标识
     * @param roundNo 补件轮次，从 1 开始
     * @param submittedBy 提交用户
     * @param comment 补件说明
     */
    public SupplierSupplement(
        String tenantId,
        UUID applicationId,
        int roundNo,
        UUID submittedBy,
        String comment
    ) {
        this.id = UUID.randomUUID();
        this.tenantId = Objects.requireNonNull(tenantId);
        this.applicationId = Objects.requireNonNull(applicationId);
        this.roundNo = roundNo;
        this.submittedBy = Objects.requireNonNull(submittedBy);
        this.comment = Objects.requireNonNull(comment);
        this.createdAt = Instant.now();
    }

    /** @return 补件记录标识 */
    public UUID getId() { return id; }

    /** @return 租户标识 */
    public String getTenantId() { return tenantId; }

    /** @return 申请标识 */
    public UUID getApplicationId() { return applicationId; }

    /** @return 补件轮次 */
    public int getRoundNo() { return roundNo; }

    /** @return 提交用户 */
    public UUID getSubmittedBy() { return submittedBy; }

    /** @return 补件说明 */
    public String getComment() { return comment; }

    /** @return 创建时间 */
    public Instant getCreatedAt() { return createdAt; }
}

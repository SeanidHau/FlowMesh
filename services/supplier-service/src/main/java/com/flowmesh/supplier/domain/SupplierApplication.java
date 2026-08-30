package com.flowmesh.supplier.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 供应商准入申请实体。
 *
 * <p>创建即 SUBMITTED，state_version 由 JPA {@link Version} 自动维护。</p>
 */
@Entity
@Table(name = "supplier_applications")
public class SupplierApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false, length = 64)
    private String tenantId;

    @Column(name = "applicant_user_id", nullable = false, updatable = false)
    private UUID applicantUserId;

    @Column(name = "supplier_name", nullable = false, length = 255)
    private String supplierName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ApplicationStatus status;

    @Version
    @Column(name = "state_version", nullable = false)
    private long stateVersion;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    /**
     * 供 JPA 重建实体状态使用。
     */
    protected SupplierApplication() {
    }

    /**
     * 创建一个状态为 SUBMITTED 的供应商申请。
     *
     * @param tenantId 租户标识
     * @param applicantUserId 申请人用户标识
     * @param supplierName 供应商名称
     */
    public SupplierApplication(String tenantId, UUID applicantUserId, String supplierName) {
        this.tenantId = Objects.requireNonNull(tenantId);
        this.applicantUserId = Objects.requireNonNull(applicantUserId);
        this.supplierName = Objects.requireNonNull(supplierName);
        this.status = ApplicationStatus.SUBMITTED;
        this.stateVersion = 0;
    }

    public UUID getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public UUID getApplicantUserId() {
        return applicantUserId;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    /**
     * 应用 workflow 审批结果，推进供应商申请状态。
     *
     * @param taskKey 已完成的 workflow 任务键
     * @throws IllegalStateException 当前申请已启用或任务顺序不合法
     */
    public void applyWorkflowTask(String taskKey) {
        if (status == ApplicationStatus.ENABLED) {
            throw new IllegalStateException("申请已经启用");
        }
        if (status == ApplicationStatus.SUBMITTED && !"PURCHASER_REVIEW".equals(taskKey)) {
            throw new IllegalStateException("申请尚未完成采购初审");
        }
        status = "OPERATIONS_ACTIVATION".equals(taskKey)
            ? ApplicationStatus.ENABLED
            : ApplicationStatus.IN_REVIEW;
    }

    public long getStateVersion() {
        return stateVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    private void onUpdate() {
        this.updatedAt = Instant.now();
    }
}

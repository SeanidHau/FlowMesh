package com.flowmesh.supplier.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 供应商准入申请实体。
 *
 * <p>创建即 SUBMITTED，state_version 由 MyBatis 条件更新语句维护。</p>
 */
public class SupplierApplication {

    private UUID id;

    private String tenantId;

    private UUID applicantUserId;

    private String supplierName;

    private ApplicationStatus status;

    private long stateVersion;

    private int supplementCount;

    private Instant createdAt;

    private Instant updatedAt;

    /**
     * 供 MyBatis 重建持久化对象状态使用。
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
        Instant now = Instant.now();
        this.id = UUID.randomUUID();
        this.tenantId = Objects.requireNonNull(tenantId);
        this.applicantUserId = Objects.requireNonNull(applicantUserId);
        this.supplierName = Objects.requireNonNull(supplierName);
        this.status = ApplicationStatus.SUBMITTED;
        this.stateVersion = 0;
        this.supplementCount = 0;
        this.createdAt = now;
        this.updatedAt = now;
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
     * 获取已使用的补件次数。
     *
     * @return 补件次数
     */
    public int getSupplementCount() {
        return supplementCount;
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
        if (status == ApplicationStatus.SUPPLEMENT_REQUIRED) {
            throw new IllegalStateException("申请仍等待补件");
        }
        status = "OPERATIONS_ACTIVATION".equals(taskKey)
            ? ApplicationStatus.ENABLED
            : ApplicationStatus.IN_REVIEW;
    }

    /**
     * 将申请标记为等待补件。
     */
    public void requestSupplement() {
        if (status != ApplicationStatus.IN_REVIEW || supplementCount >= 2) {
            throw new IllegalStateException("申请当前不允许补件或补件次数已达上限");
        }
        status = ApplicationStatus.SUPPLEMENT_REQUIRED;
    }

    /**
     * 提交一轮补件并重新进入采购初审等待状态。
     */
    public void submitSupplement() {
        if (status != ApplicationStatus.SUPPLEMENT_REQUIRED || supplementCount >= 2) {
            throw new IllegalStateException("申请当前不允许提交补件");
        }
        supplementCount++;
        status = ApplicationStatus.SUBMITTED;
    }

    /**
     * 应用风控拒绝结果，进入不可继续审批的终态。
     */
    public void rejectRisk() {
        if (status != ApplicationStatus.SUBMITTED) {
            throw new IllegalStateException("申请当前不允许应用风控拒绝结果");
        }
        status = ApplicationStatus.REJECTED;
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

}

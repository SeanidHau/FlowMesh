package com.flowmesh.workflow.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 流程实例中的持久化审批任务。
 *
 * <p>任务以流程实例和任务键建立唯一约束。法务与财务任务可以同时处于
 * {@link WorkflowTaskStatus#PENDING}，由任务行锁保证同一任务不会被重复完成。</p>
 */
public class WorkflowTaskRecord {

    private UUID id;
    private UUID workflowInstanceId;
    private String tenantId;
    private UUID applicationId;
    private WorkflowTask taskKey;
    private WorkflowTaskStatus status;
    private UUID completedBy;
    private Instant completedAt;
    private Instant createdAt;

    /**
     * 供 MyBatis 重建持久化对象状态使用。
     */
    protected WorkflowTaskRecord() {
    }

    /**
     * 创建待处理任务。
     *
     * @param workflowInstanceId 流程实例标识
     * @param tenantId 租户标识
     * @param applicationId 申请标识
     * @param taskKey 任务键
     */
    public WorkflowTaskRecord(
        UUID workflowInstanceId,
        String tenantId,
        UUID applicationId,
        WorkflowTask taskKey
    ) {
        this.id = UUID.randomUUID();
        this.workflowInstanceId = Objects.requireNonNull(workflowInstanceId);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.applicationId = Objects.requireNonNull(applicationId);
        this.taskKey = Objects.requireNonNull(taskKey);
        this.status = WorkflowTaskStatus.PENDING;
        this.createdAt = Instant.now();
    }

    /**
     * 获取任务标识。
     *
     * @return 任务标识
     */
    public UUID getId() {
        return id;
    }

    /**
     * 获取流程实例标识。
     *
     * @return 流程实例标识
     */
    public UUID getWorkflowInstanceId() {
        return workflowInstanceId;
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
     * 获取申请标识。
     *
     * @return 申请标识
     */
    public UUID getApplicationId() {
        return applicationId;
    }

    /**
     * 获取任务键。
     *
     * @return 任务键
     */
    public WorkflowTask getTaskKey() {
        return taskKey;
    }

    /**
     * 获取任务状态。
     *
     * @return 任务状态
     */
    public WorkflowTaskStatus getStatus() {
        return status;
    }

    /**
     * 获取完成任务的用户。
     *
     * @return 用户标识；未完成时为空
     */
    public UUID getCompletedBy() {
        return completedBy;
    }

    /**
     * 获取任务完成时间。
     *
     * @return 完成时间；未完成时为空
     */
    public Instant getCompletedAt() {
        return completedAt;
    }

    /**
     * 获取任务创建时间。
     *
     * @return 创建时间
     */
    public Instant getCreatedAt() {
        return createdAt;
    }
}

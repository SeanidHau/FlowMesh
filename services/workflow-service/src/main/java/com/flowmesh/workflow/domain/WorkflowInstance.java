package com.flowmesh.workflow.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 供应商准入流程实例的最小持久化投影。
 *
 * <p>{@code sourceEventId} 唯一约束是消费者幂等边界：同一提交事件重复到达时，
 * 只保留一个流程实例。</p>
 */
public class WorkflowInstance {

    private UUID id;

    private UUID applicationId;

    private UUID sourceEventId;

    private String tenantId;

    private String processDefinitionKey;

    private WorkflowInstanceStatus status;

    private WorkflowTask currentTask;

    private long version;

    private Instant createdAt;

    /**
     * 供 MyBatis 重建持久化对象状态使用。
     */
    protected WorkflowInstance() {
    }

    /**
     * 创建已启动的供应商准入流程投影。
     *
     * @param applicationId 供应商申请标识
     * @param sourceEventId 触发流程的领域事件标识
     * @param tenantId 租户标识
     */
    public WorkflowInstance(UUID applicationId, UUID sourceEventId, String tenantId) {
        this.id = UUID.randomUUID();
        this.applicationId = Objects.requireNonNull(applicationId);
        this.sourceEventId = Objects.requireNonNull(sourceEventId);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.processDefinitionKey = "supplier-onboarding";
        this.status = WorkflowInstanceStatus.IN_PROGRESS;
        this.currentTask = WorkflowTask.PURCHASER_REVIEW;
        this.version = 0;
        this.createdAt = Instant.now();
    }

    /**
     * 获取流程实例标识。
     *
     * @return 流程实例 ID
     */
    public UUID getId() {
        return id;
    }

    /**
     * 获取供应商申请标识。
     *
     * @return 申请 ID
     */
    public UUID getApplicationId() {
        return applicationId;
    }

    /**
     * 获取触发流程的事件标识。
     *
     * @return 事件 ID
     */
    public UUID getSourceEventId() {
        return sourceEventId;
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
     * 获取流程定义键。
     *
     * @return 流程定义键
     */
    public String getProcessDefinitionKey() {
        return processDefinitionKey;
    }

    /**
     * 获取流程状态。
     *
     * @return 流程状态
     */
    public WorkflowInstanceStatus getStatus() {
        return status;
    }

    /**
     * 获取当前待处理节点。
     *
     * @return 当前审批节点；流程完成后为 {@code null}
     */
    public WorkflowTask getCurrentTask() {
        return currentTask;
    }

    /**
     * 获取乐观锁版本。
     *
     * @return 当前版本
     */
    public long getVersion() {
        return version;
    }

    /**
     * 在 MyBatis 条件更新成功后同步内存中的版本号。
     */
    public void incrementVersion() {
        version++;
    }

    /**
     * 完成当前审批节点并推进流程。
     */
    public void completeCurrentTask() {
        WorkflowTask next = Objects.requireNonNull(currentTask).next();
        if (next == null) {
            status = WorkflowInstanceStatus.COMPLETED;
            currentTask = null;
            return;
        }
        currentTask = next;
    }

    /**
     * 获取创建时间。
     *
     * @return 创建时间
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

}

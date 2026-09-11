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

    private UUID applicantUserId;

    private UUID sourceEventId;

    private String tenantId;

    private String processDefinitionKey;

    private WorkflowInstanceStatus status;

    private WorkflowTask currentTask;

    private long version;

    private int reviewRound;

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
        this(applicationId, sourceEventId, tenantId, null);
    }

    /**
     * 创建带申请人的供应商准入流程投影。
     *
     * @param applicationId 供应商申请标识
     * @param sourceEventId 触发流程的领域事件标识
     * @param tenantId 租户标识
     * @param applicantUserId 申请人用户标识
     */
    public WorkflowInstance(
        UUID applicationId,
        UUID sourceEventId,
        String tenantId,
        UUID applicantUserId
    ) {
        this.id = UUID.randomUUID();
        this.applicationId = Objects.requireNonNull(applicationId);
        this.sourceEventId = Objects.requireNonNull(sourceEventId);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.applicantUserId = applicantUserId;
        this.processDefinitionKey = "supplier-onboarding";
        this.status = WorkflowInstanceStatus.RISK_CHECKING;
        this.currentTask = null;
        this.version = 0;
        this.reviewRound = 1;
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
     * 获取申请人用户标识。
     *
     * @return 申请人用户标识；历史迁移数据可能为空
     */
    public UUID getApplicantUserId() {
        return applicantUserId;
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
     * 获取当前审批轮次。
     *
     * @return 从 1 开始的审批轮次
     */
    public int getReviewRound() {
        return reviewRound;
    }

    /**
     * 在 MyBatis 条件更新成功后同步内存中的版本号。
     */
    public void incrementVersion() {
        version++;
    }

    /**
     * 根据持久化任务状态推进流程摘要。
     *
     * <p>法务和财务是并行任务。一个任务完成后，摘要指向另一个仍待处理的任务；两个任务
     * 都完成后才进入运营启用。</p>
     *
     * @param completedTask 已完成任务
     * @param parallelReviewTaskPending 另一个并行会签任务是否仍待处理
     */
    public void advanceAfterTask(WorkflowTask completedTask, boolean parallelReviewTaskPending) {
        if (completedTask == WorkflowTask.PURCHASER_REVIEW) {
            currentTask = WorkflowTask.LEGAL_REVIEW;
            return;
        }
        if (completedTask.isParallelReview()) {
            if (parallelReviewTaskPending) {
                currentTask = completedTask == WorkflowTask.LEGAL_REVIEW
                    ? WorkflowTask.FINANCE_REVIEW : WorkflowTask.LEGAL_REVIEW;
            } else {
                currentTask = WorkflowTask.OPERATIONS_ACTIVATION;
            }
            return;
        }
        if (completedTask == WorkflowTask.OPERATIONS_ACTIVATION) {
            status = WorkflowInstanceStatus.COMPLETED;
            currentTask = null;
            return;
        }
        if (completedTask == WorkflowTask.OPERATIONS_ESCALATION) {
            currentTask = WorkflowTask.OPERATIONS_ACTIVATION;
            return;
        }
        throw new IllegalStateException("流程任务不受支持：" + completedTask);
    }

    /**
     * 将当前流程退回补件，并清空流程摘要中的可操作节点。
     *
     * @param returnedTask 被退回的审批任务
     */
    public void requestSupplement(WorkflowTask returnedTask) {
        if (status != WorkflowInstanceStatus.IN_PROGRESS || returnedTask == null || !canRequestSupplement()) {
            throw new IllegalStateException("流程当前不允许退回补件");
        }
        status = WorkflowInstanceStatus.SUPPLEMENT_REQUIRED;
        currentTask = null;
    }

    /**
     * 判断当前轮次是否仍允许发起补件。
     *
     * <p>第 1 轮审批退回后产生第 1 次补件，第 2 轮审批退回后产生第 2 次补件；
     * 第 3 轮不再允许继续退回，避免供应商状态和流程状态出现分歧。</p>
     *
     * @return 仍可补件时为 {@code true}
     */
    public boolean canRequestSupplement() {
        return reviewRound < 3;
    }

    /**
     * 接收申请人的补件提交，开启下一轮采购初审。
     */
    public void startSupplementReview() {
        if (status != WorkflowInstanceStatus.SUPPLEMENT_REQUIRED || currentTask != null) {
            throw new IllegalStateException("流程当前不允许重新开始补件审核");
        }
        reviewRound++;
        status = WorkflowInstanceStatus.IN_PROGRESS;
        currentTask = WorkflowTask.PURCHASER_REVIEW;
    }

    /**
     * 风控通过后启动采购初审。
     */
    public void startProcurementReview() {
        if (status != WorkflowInstanceStatus.RISK_CHECKING || currentTask != null) {
            throw new IllegalStateException("流程当前不允许启动采购初审");
        }
        status = WorkflowInstanceStatus.IN_PROGRESS;
        currentTask = WorkflowTask.PURCHASER_REVIEW;
    }

    /**
     * 风控拒绝后终止流程。
     */
    public void rejectRisk() {
        if (status != WorkflowInstanceStatus.RISK_CHECKING || currentTask != null) {
            throw new IllegalStateException("流程当前不允许被风控终止");
        }
        status = WorkflowInstanceStatus.REJECTED;
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

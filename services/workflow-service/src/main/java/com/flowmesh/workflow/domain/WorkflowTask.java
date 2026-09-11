package com.flowmesh.workflow.domain;

/**
 * 供应商准入流程的审批任务键和角色映射。
 *
 * <p>采购初审完成后，法务和财务任务同时创建。二者都完成后才创建运营启用任务。</p>
 */
public enum WorkflowTask {
    PURCHASER_REVIEW("PURCHASER"),
    LEGAL_REVIEW("LEGAL"),
    FINANCE_REVIEW("FINANCE"),
    OPERATIONS_ACTIVATION("OPERATIONS"),
    OPERATIONS_ESCALATION("OPERATIONS");

    private final String requiredRole;

    WorkflowTask(String requiredRole) {
        this.requiredRole = requiredRole;
    }

    /**
     * 获取完成该节点所需的角色。
     *
     * @return 角色编码
     */
    public String getRequiredRole() {
        return requiredRole;
    }

    /**
     * 判断任务是否属于并行会签阶段。
     *
     * @return 法务或财务任务时为 {@code true}
     */
    public boolean isParallelReview() {
        return this == LEGAL_REVIEW || this == FINANCE_REVIEW;
    }

    /**
     * 判断任务是否属于 SLA 超时后的运营处置节点。
     *
     * @return 运营升级任务时为 {@code true}
     */
    public boolean isSlaEscalation() {
        return this == OPERATIONS_ESCALATION;
    }

    /**
     * 获取完成当前节点后的下一个节点。
     *
     * @return 下一个节点；当前节点为末节点时返回 {@code null}
     */
    public WorkflowTask next() {
        return switch (this) {
            case PURCHASER_REVIEW -> LEGAL_REVIEW;
            case LEGAL_REVIEW -> FINANCE_REVIEW;
            case FINANCE_REVIEW -> OPERATIONS_ACTIVATION;
            case OPERATIONS_ACTIVATION, OPERATIONS_ESCALATION -> null;
        };
    }
}

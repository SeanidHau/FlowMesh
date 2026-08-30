package com.flowmesh.workflow.domain;

/**
 * 供应商准入 MVP 的审批节点顺序。
 */
public enum WorkflowTask {
    PURCHASER_REVIEW("PURCHASER"),
    LEGAL_REVIEW("LEGAL"),
    FINANCE_REVIEW("FINANCE"),
    OPERATIONS_ACTIVATION("OPERATIONS");

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
     * 获取完成当前节点后的下一个节点。
     *
     * @return 下一个节点；当前节点为末节点时返回 {@code null}
     */
    public WorkflowTask next() {
        return switch (this) {
            case PURCHASER_REVIEW -> LEGAL_REVIEW;
            case LEGAL_REVIEW -> FINANCE_REVIEW;
            case FINANCE_REVIEW -> OPERATIONS_ACTIVATION;
            case OPERATIONS_ACTIVATION -> null;
        };
    }
}

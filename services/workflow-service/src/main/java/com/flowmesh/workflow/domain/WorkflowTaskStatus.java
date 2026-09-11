package com.flowmesh.workflow.domain;

/**
 * 持久化审批任务的生命周期状态。
 */
public enum WorkflowTaskStatus {
    /** 等待具备对应角色的用户处理。 */
    PENDING,
    /** 已完成并记录操作者。 */
    COMPLETED,
    /** 被审批人退回，等待申请人补件。 */
    RETURNED,
    /** 因 SLA 超时转入运营处置。 */
    ESCALATED,
    /** 因流程分支变化而取消。 */
    CANCELLED
}

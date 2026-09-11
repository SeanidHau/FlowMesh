package com.flowmesh.workflow.domain;

/**
 * 审批任务的业务决定。
 */
public enum WorkflowTaskDecision {
    /** 同意当前任务并推进流程。 */
    APPROVE,
    /** 退回申请人补充材料后重新审核。 */
    RETURN_FOR_SUPPLEMENT
}

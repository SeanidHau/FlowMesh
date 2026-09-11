package com.flowmesh.workflow.domain;

/**
 * 最小流程实例状态。
 */
public enum WorkflowInstanceStatus {
    RISK_CHECKING,
    IN_PROGRESS,
    SUPPLEMENT_REQUIRED,
    REJECTED,
    COMPLETED
}

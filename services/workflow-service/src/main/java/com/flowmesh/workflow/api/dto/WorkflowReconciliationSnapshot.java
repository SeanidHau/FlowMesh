package com.flowmesh.workflow.api.dto;

/**
 * workflow 提供给 supplier 对账使用的只读快照。
 *
 * @param status 流程状态
 * @param currentTask 当前任务
 * @param outboxPendingCount 待发送事件数
 * @param outboxEventCount Outbox 事件总数
 */
public record WorkflowReconciliationSnapshot(
    String status,
    String currentTask,
    long outboxPendingCount,
    long outboxEventCount
) {
}

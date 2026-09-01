package com.flowmesh.supplier.application;

import java.util.Optional;
import java.util.UUID;

/**
 * 读取 workflow-service 的对账快照。
 */
public interface WorkflowStateClient {

    /**
     * 查询指定租户和申请的 workflow 状态。
     *
     * @param tenantId 租户标识
     * @param applicationId 申请标识
     * @param bearerToken 当前访问令牌
     * @return workflow 快照；不存在时为空
     */
    Optional<WorkflowState> find(String tenantId, UUID applicationId, String bearerToken);

    /**
     * workflow 对账快照。
     *
     * @param status 流程状态
     * @param currentTask 当前任务
     * @param outboxPendingCount 待发送事件数
     * @param outboxEventCount Outbox 事件总数
     */
    record WorkflowState(
        String status,
        String currentTask,
        long outboxPendingCount,
        long outboxEventCount
    ) {
    }
}

package com.flowmesh.workflow.api.dto;

import com.flowmesh.workflow.domain.WorkflowInstance;
import java.time.Instant;
import java.util.UUID;

/**
 * 流程实例 API 响应。
 *
 * @param id 流程实例标识
 * @param applicationId 申请标识
 * @param tenantId 租户标识
 * @param processDefinitionKey 流程定义键
 * @param status 流程状态
 * @param currentTask 当前任务键
 * @param version 乐观锁版本
 * @param createdAt 创建时间
 */
public record WorkflowInstanceResponse(
    UUID id,
    UUID applicationId,
    String tenantId,
    String processDefinitionKey,
    String status,
    String currentTask,
    long version,
    Instant createdAt
) {

    /**
     * 从领域实体创建 API 响应。
     *
     * @param instance 流程实例
     * @return API 响应
     */
    public static WorkflowInstanceResponse from(WorkflowInstance instance) {
        return new WorkflowInstanceResponse(
            instance.getId(),
            instance.getApplicationId(),
            instance.getTenantId(),
            instance.getProcessDefinitionKey(),
            instance.getStatus().name(),
            instance.getCurrentTask() == null ? null : instance.getCurrentTask().name(),
            instance.getVersion(),
            instance.getCreatedAt()
        );
    }
}

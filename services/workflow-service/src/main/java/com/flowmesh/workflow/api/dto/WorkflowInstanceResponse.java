package com.flowmesh.workflow.api.dto;

import com.flowmesh.workflow.domain.WorkflowInstance;
import com.flowmesh.workflow.domain.WorkflowTask;
import java.time.Instant;
import java.util.List;
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
 * @param availableTasks 当前待处理任务键；法务和财务可以同时存在
 * @param completedTasks 已完成任务键
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
    List<String> availableTasks,
    List<String> completedTasks,
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
        return from(instance, instance.getCurrentTask() == null
            ? List.of() : List.of(instance.getCurrentTask()), List.of());
    }

    /**
     * 从领域实体和持久化待办列表创建 API 响应。
     *
     * @param instance 流程实例
     * @param pendingTasks 当前待办任务
     * @return API 响应
     */
    public static WorkflowInstanceResponse from(
        WorkflowInstance instance,
        List<WorkflowTask> pendingTasks,
        List<WorkflowTask> completedTasks
    ) {
        return new WorkflowInstanceResponse(
            instance.getId(),
            instance.getApplicationId(),
            instance.getTenantId(),
            instance.getProcessDefinitionKey(),
            instance.getStatus().name(),
            instance.getCurrentTask() == null ? null : instance.getCurrentTask().name(),
            pendingTasks.stream().map(WorkflowTask::name).toList(),
            completedTasks.stream().map(WorkflowTask::name).toList(),
            instance.getVersion(),
            instance.getCreatedAt()
        );
    }
}

package com.flowmesh.workflow.application;

import com.flowmesh.common.security.AuthPrincipal;
import com.flowmesh.workflow.domain.WorkflowInstance;
import com.flowmesh.workflow.domain.WorkflowInstanceStatus;
import com.flowmesh.workflow.domain.WorkflowTask;
import com.flowmesh.workflow.repository.WorkflowInstanceRepository;
import com.flowmesh.workflow.rls.TenantRlsInitializer;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 提供流程实例查询和最小审批节点推进能力。
 */
@Service
public class WorkflowInstanceService {

    private final WorkflowInstanceRepository repository;
    private final TenantRlsInitializer tenantRlsInitializer;

    /**
     * 创建流程实例应用服务。
     *
     * @param repository 流程实例仓储
     * @param tenantRlsInitializer 租户 RLS 初始化器
     */
    public WorkflowInstanceService(
        WorkflowInstanceRepository repository,
        TenantRlsInitializer tenantRlsInitializer
    ) {
        this.repository = repository;
        this.tenantRlsInitializer = tenantRlsInitializer;
    }

    /**
     * 查询当前租户下的流程实例。
     *
     * @param tenantId 租户标识
     * @param applicationId 申请标识
     * @return 流程实例
     */
    @Transactional(readOnly = true)
    public WorkflowInstance find(String tenantId, UUID applicationId) {
        tenantRlsInitializer.initialize(tenantId);
        return repository.findByApplicationId(applicationId)
            .orElseThrow(WorkflowInstanceNotFoundException::new);
    }

    /**
     * 校验角色并完成当前审批节点。
     *
     * @param principal 已认证主体
     * @param applicationId 申请标识
     * @param taskKey 客户端提交的任务键
     * @return 推进后的流程实例
     */
    @Transactional
    public WorkflowInstance completeTask(
        AuthPrincipal principal,
        UUID applicationId,
        String taskKey
    ) {
        tenantRlsInitializer.initialize(principal.tenantId());
        WorkflowInstance instance = repository.findByApplicationId(applicationId)
            .orElseThrow(WorkflowInstanceNotFoundException::new);

        WorkflowTask task;
        try {
            task = WorkflowTask.valueOf(taskKey);
        } catch (IllegalArgumentException exception) {
            throw new WorkflowTaskConflictException();
        }

        if (instance.getStatus() != WorkflowInstanceStatus.IN_PROGRESS
            || instance.getCurrentTask() != task) {
            throw new WorkflowTaskConflictException();
        }
        if (!principal.roles().contains(task.getRequiredRole())) {
            throw new WorkflowTaskForbiddenException();
        }

        instance.completeCurrentTask();
        return repository.save(instance);
    }
}

package com.flowmesh.workflow.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmesh.common.security.AuthPrincipal;
import com.flowmesh.workflow.domain.WorkflowInstance;
import com.flowmesh.workflow.domain.WorkflowInstanceStatus;
import com.flowmesh.workflow.domain.WorkflowTask;
import com.flowmesh.workflow.domain.WorkflowOutboxEvent;
import com.flowmesh.workflow.repository.WorkflowInstanceRepository;
import com.flowmesh.workflow.repository.WorkflowOutboxEventRepository;
import com.flowmesh.workflow.rls.TenantRlsInitializer;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 提供流程实例查询和最小审批节点推进能力。
 */
@Service
public class WorkflowInstanceService {

    private final WorkflowInstanceRepository repository;
    private final WorkflowOutboxEventRepository outboxRepository;
    private final TenantRlsInitializer tenantRlsInitializer;
    private final ObjectMapper objectMapper;

    /**
     * 创建流程实例应用服务。
     *
     * @param repository 流程实例仓储
     * @param outboxRepository workflow 事件 Outbox 仓储
     * @param tenantRlsInitializer 租户 RLS 初始化器
     * @param objectMapper JSON 序列化器
     */
    public WorkflowInstanceService(
        WorkflowInstanceRepository repository,
        WorkflowOutboxEventRepository outboxRepository,
        TenantRlsInitializer tenantRlsInitializer,
        ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.outboxRepository = outboxRepository;
        this.tenantRlsInitializer = tenantRlsInitializer;
        this.objectMapper = objectMapper;
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
     * @param traceId 链路追踪标识
     * @return 推进后的流程实例
     */
    @Transactional
    public WorkflowInstance completeTask(
        AuthPrincipal principal,
        UUID applicationId,
        String taskKey,
        String traceId
    ) {
        String effectiveTraceId = traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId;
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
        if (repository.updateState(instance) != 1) {
            throw new OptimisticLockingFailureException("流程状态已被其他事务更新");
        }
        instance.incrementVersion();
        WorkflowInstance saved = instance;
        UUID eventId = UUID.randomUUID();
        outboxRepository.save(new WorkflowOutboxEvent(
            eventId,
            principal.tenantId(),
            applicationId,
            "workflow-events",
            "WorkflowTaskCompleted",
            writeJson(new WorkflowTaskCompletedMessage(
                eventId,
                "WorkflowTaskCompleted",
                1,
                principal.tenantId(),
                applicationId,
                Instant.now(),
                effectiveTraceId,
                new TaskCompletedPayload(task.name())
            ))
        ));
        return saved;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("workflow 事件序列化失败", exception);
        }
    }

    /**
     * workflow 审批完成事件信封。
     *
     * @param eventId 事件标识
     * @param eventType 事件类型
     * @param schemaVersion 事件结构版本
     * @param tenantId 租户标识
     * @param aggregateId 申请标识
     * @param occurredAt 事件发生时间
     * @param traceId 链路追踪标识
     * @param payload 事件载荷
     */
    private record WorkflowTaskCompletedMessage(
        UUID eventId,
        String eventType,
        int schemaVersion,
        String tenantId,
        UUID aggregateId,
        Instant occurredAt,
        String traceId,
        TaskCompletedPayload payload
    ) {
    }

    /**
     * 审批完成事件载荷。
     *
     * @param taskKey 已完成的任务键
     */
    private record TaskCompletedPayload(String taskKey) {
    }
}

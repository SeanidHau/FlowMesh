package com.flowmesh.workflow.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmesh.common.security.AuthPrincipal;
import com.flowmesh.workflow.domain.WorkflowInstance;
import com.flowmesh.workflow.domain.WorkflowInstanceStatus;
import com.flowmesh.workflow.domain.WorkflowTask;
import com.flowmesh.workflow.domain.WorkflowTaskRecord;
import com.flowmesh.workflow.domain.WorkflowOutboxEvent;
import com.flowmesh.workflow.api.dto.WorkflowReconciliationSnapshot;
import com.flowmesh.workflow.repository.WorkflowInstanceRepository;
import com.flowmesh.workflow.repository.WorkflowOutboxEventRepository;
import com.flowmesh.workflow.repository.WorkflowTaskRepository;
import com.flowmesh.workflow.rls.TenantRlsInitializer;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * 提供流程实例查询和最小审批节点推进能力。
 */
@Service
public class WorkflowInstanceService {

    private final WorkflowInstanceRepository repository;
    private final WorkflowOutboxEventRepository outboxRepository;
    private final TenantRlsInitializer tenantRlsInitializer;
    private final ObjectMapper objectMapper;
    private final Counter taskCompletedCounter;
    private final WorkflowTaskRepository taskRepository;

    /**
     * 创建流程实例应用服务。
     *
     * @param repository 流程实例仓储
     * @param outboxRepository workflow 事件 Outbox 仓储
     * @param tenantRlsInitializer 租户 RLS 初始化器
     * @param objectMapper JSON 序列化器
     * @param meterRegistry Micrometer 指标注册器
     * @param taskRepository 审批任务仓储
     */
    public WorkflowInstanceService(
        WorkflowInstanceRepository repository,
        WorkflowOutboxEventRepository outboxRepository,
        TenantRlsInitializer tenantRlsInitializer,
        ObjectMapper objectMapper,
        MeterRegistry meterRegistry,
        WorkflowTaskRepository taskRepository
    ) {
        this.repository = repository;
        this.outboxRepository = outboxRepository;
        this.tenantRlsInitializer = tenantRlsInitializer;
        this.objectMapper = objectMapper;
        this.taskRepository = taskRepository;
        this.taskCompletedCounter = Counter.builder("flowmesh.workflow.task.completed")
            .description("workflow 审批节点完成次数")
            .register(meterRegistry);
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
     * 查询流程实例的当前待办任务。
     *
     * @param instance 流程实例
     * @return 当前待办任务，按业务顺序排列
     */
    @Transactional(readOnly = true)
    public java.util.List<WorkflowTask> pendingTasks(String tenantId, WorkflowInstance instance) {
        tenantRlsInitializer.initialize(tenantId);
        return taskRepository.findPendingByInstanceId(instance.getId()).stream()
            .map(WorkflowTaskRecord::getTaskKey)
            .toList();
    }

    /**
     * 查询流程实例已完成的任务。
     *
     * @param tenantId 租户标识
     * @param instance 流程实例
     * @return 已完成任务，按完成时间排序
     */
    @Transactional(readOnly = true)
    public java.util.List<WorkflowTask> completedTasks(String tenantId, WorkflowInstance instance) {
        tenantRlsInitializer.initialize(tenantId);
        return taskRepository.findCompletedByInstanceId(instance.getId()).stream()
            .map(WorkflowTaskRecord::getTaskKey)
            .toList();
    }

    /**
     * 读取供跨服务对账使用的流程快照。
     *
     * @param tenantId 租户标识
     * @param applicationId 申请标识
     * @return 流程和 Outbox 快照
     */
    @Transactional(readOnly = true)
    public WorkflowReconciliationSnapshot snapshot(String tenantId, UUID applicationId) {
        WorkflowInstance instance = find(tenantId, applicationId);
        return new WorkflowReconciliationSnapshot(
            instance.getStatus().name(),
            instance.getCurrentTask() == null ? null : instance.getCurrentTask().name(),
            outboxRepository.countPendingByTenantIdAndAggregateIdAndTag(tenantId, applicationId, null),
            outboxRepository.countByTenantIdAndAggregateIdAndTag(tenantId, applicationId, null)
        );
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
            || (instance.getCurrentTask() != task && !task.isParallelReview())) {
            throw new WorkflowTaskConflictException();
        }
        if (!principal.roles().contains(task.getRequiredRole())) {
            throw new WorkflowTaskForbiddenException();
        }

        WorkflowTaskRecord taskRecord = taskRepository
            .findPendingByInstanceIdAndTaskForUpdate(instance.getId(), task)
            .orElseThrow(WorkflowTaskConflictException::new);
        if (taskRepository.markCompleted(taskRecord.getId(), principal.userId(), Instant.now()) != 1) {
            throw new WorkflowTaskConflictException();
        }

        boolean parallelReviewTaskPending = false;
        if (task.isParallelReview()) {
            WorkflowTask otherTask = task == WorkflowTask.LEGAL_REVIEW
                ? WorkflowTask.FINANCE_REVIEW : WorkflowTask.LEGAL_REVIEW;
            parallelReviewTaskPending = taskRepository
                .existsPendingByInstanceIdAndTask(instance.getId(), otherTask);
        }
        instance.advanceAfterTask(task, parallelReviewTaskPending);
        if (repository.updateState(instance) != 1) {
            throw new OptimisticLockingFailureException("流程状态已被其他事务更新");
        }
        instance.incrementVersion();

        if (task == WorkflowTask.PURCHASER_REVIEW) {
            taskRepository.insert(new WorkflowTaskRecord(
                instance.getId(), principal.tenantId(), applicationId, WorkflowTask.LEGAL_REVIEW
            ));
            taskRepository.insert(new WorkflowTaskRecord(
                instance.getId(), principal.tenantId(), applicationId, WorkflowTask.FINANCE_REVIEW
            ));
        } else if (task.isParallelReview() && !parallelReviewTaskPending) {
            taskRepository.insert(new WorkflowTaskRecord(
                instance.getId(), principal.tenantId(), applicationId, WorkflowTask.OPERATIONS_ACTIVATION
            ));
        }
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
        taskCompletedCounter.increment();
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

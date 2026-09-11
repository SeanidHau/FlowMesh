package com.flowmesh.workflow.repository;

import com.flowmesh.workflow.domain.WorkflowTask;
import com.flowmesh.workflow.domain.WorkflowTaskRecord;
import com.flowmesh.workflow.domain.WorkflowTaskStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 使用 MyBatis 访问持久化审批任务。
 */
@Mapper
public interface WorkflowTaskRepository {

    /**
     * 查询流程实例的待处理任务，并按业务顺序返回。
     *
     * @param workflowInstanceId 流程实例标识
     * @return 待处理任务
     */
    List<WorkflowTaskRecord> findPendingByInstanceId(@Param("workflowInstanceId") UUID workflowInstanceId);

    /**
     * 查询流程实例已完成的任务。
     *
     * @param workflowInstanceId 流程实例标识
     * @return 已完成任务
     */
    List<WorkflowTaskRecord> findCompletedByInstanceId(@Param("workflowInstanceId") UUID workflowInstanceId);

    /**
     * 锁定指定待办任务，防止同一任务被并发完成。
     *
     * @param workflowInstanceId 流程实例标识
     * @param roundNo 审批轮次
     * @param taskKey 任务键
     * @return 待处理任务；不存在或已完成时为空
     */
    Optional<WorkflowTaskRecord> findPendingByInstanceIdAndTaskForUpdate(
        @Param("workflowInstanceId") UUID workflowInstanceId,
        @Param("roundNo") int roundNo,
        @Param("taskKey") WorkflowTask taskKey
    );

    /**
     * 判断指定任务是否仍待处理。
     *
     * @param workflowInstanceId 流程实例标识
     * @param roundNo 审批轮次
     * @param taskKey 任务键
     * @return 仍待处理时为 {@code true}
     */
    boolean existsPendingByInstanceIdAndTask(
        @Param("workflowInstanceId") UUID workflowInstanceId,
        @Param("roundNo") int roundNo,
        @Param("taskKey") WorkflowTask taskKey
    );

    /**
     * 插入待处理任务。
     *
     * @param task 待插入任务
     * @return 受影响行数
     */
    int insert(WorkflowTaskRecord task);

    /**
     * 完成持有行锁的任务。
     *
     * @param id 任务标识
     * @param status 完成后的任务状态
     * @param decision 审批决定
     * @param comment 审批意见
     * @param completedBy 操作者
     * @param completedAt 完成时间
     * @return 受影响行数
     */
    int markCompleted(
        @Param("id") UUID id,
        @Param("status") WorkflowTaskStatus status,
        @Param("decision") String decision,
        @Param("comment") String comment,
        @Param("completedBy") UUID completedBy,
        @Param("completedAt") Instant completedAt
    );

    /**
     * 查询尚未发送 SLA 催办的任务。
     *
     * @param now 当前时间
     * @return 到达催办时间的待办任务
     */
    List<WorkflowTaskRecord> findPendingForReminder(@Param("now") Instant now);

    /**
     * 查询已超过截止时间且尚未升级的任务。
     *
     * @param now 当前时间
     * @return 到期任务
     */
    List<WorkflowTaskRecord> findPendingForEscalation(@Param("now") Instant now);

    /**
     * 标记任务已经发出催办通知。
     *
     * @param id 任务标识
     * @param remindedAt 催办时间
     * @return 受影响行数
     */
    int markReminderSent(@Param("id") UUID id, @Param("remindedAt") Instant remindedAt);

    /**
     * 将任务标记为已因 SLA 超时升级。
     *
     * @param id 任务标识
     * @param escalatedAt 升级时间
     * @return 受影响行数
     */
    int markEscalated(@Param("id") UUID id, @Param("escalatedAt") Instant escalatedAt);

    /**
     * 取消同一轮中尚未完成的其他任务。
     *
     * @param workflowInstanceId 流程实例标识
     * @param roundNo 审批轮次
     * @return 受影响行数
     */
    int cancelPendingByInstanceAndRound(
        @Param("workflowInstanceId") UUID workflowInstanceId,
        @Param("roundNo") int roundNo
    );
}

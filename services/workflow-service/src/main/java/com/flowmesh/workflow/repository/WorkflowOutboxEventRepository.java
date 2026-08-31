package com.flowmesh.workflow.repository;

import com.flowmesh.workflow.domain.WorkflowOutboxEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 使用 MyBatis 访问 workflow 服务的 Outbox 事件。
 */
@Mapper
public interface WorkflowOutboxEventRepository {

    /**
     * 查询指定聚合和事件标签下的全部 Outbox 事件。
     *
     * @param aggregateId 聚合标识
     * @param tag 事件标签
     * @return 按创建时间升序排列的事件
     */
    List<WorkflowOutboxEvent> findAllByAggregateIdAndTag(
        @Param("aggregateId") UUID aggregateId,
        @Param("tag") String tag
    );

    /**
     * 插入 workflow Outbox 事件。
     *
     * @param event 待保存事件
     * @return 保存后的事件对象
     */
    default WorkflowOutboxEvent save(WorkflowOutboxEvent event) {
        insert(event);
        return event;
    }

    /**
     * 在短事务内认领一批可发布事件。
     *
     * @param now 当前时间
     * @param claimToken 认领令牌
     * @param claimedUntil 租约到期时间
     * @param limit 最大数量
     * @return 已认领事件
     */
    List<WorkflowOutboxEvent> claimBatch(
        @Param("now") Instant now,
        @Param("claimToken") UUID claimToken,
        @Param("claimedUntil") Instant claimedUntil,
        @Param("limit") int limit
    );

    /**
     * 只有持有认领令牌的发布器可以确认发送成功。
     *
     * @return 受影响行数
     */
    int markPublishedIfClaimed(
        @Param("id") UUID id,
        @Param("claimToken") UUID claimToken,
        @Param("publishedAt") Instant publishedAt
    );

    /**
     * 记录可重试的投递失败。
     *
     * @return 受影响行数
     */
    int recordRetryIfClaimed(
        @Param("id") UUID id,
        @Param("claimToken") UUID claimToken,
        @Param("error") String error,
        @Param("nextAttemptAt") Instant nextAttemptAt,
        @Param("maxAttempts") int maxAttempts
    );

    /**
     * 将达到最大次数的事件标记为失败终态。
     *
     * @return 受影响行数
     */
    int markDeadLetteredIfClaimed(
        @Param("id") UUID id,
        @Param("claimToken") UUID claimToken,
        @Param("error") String error,
        @Param("deadLetteredAt") Instant deadLetteredAt,
        @Param("maxAttempts") int maxAttempts
    );

    /**
     * 插入 workflow Outbox 事件记录。
     *
     * @param event 待保存事件
     * @return 受影响行数
     */
    int insert(WorkflowOutboxEvent event);
}

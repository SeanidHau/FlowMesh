package com.flowmesh.supplier.repository;

import com.flowmesh.supplier.domain.OutboxEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 使用 MyBatis 访问 supplier 服务的 Outbox 事件。
 */
@Mapper
public interface OutboxEventRepository {

    /**
     * 插入 Outbox 事件。
     *
     * @param event 待保存事件
     * @return 保存后的事件对象
     */
    default OutboxEvent save(OutboxEvent event) {
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
    List<OutboxEvent> claimBatch(
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
     * 按业务聚合读取其 Outbox 事件。
     *
     * @param aggregateId 业务聚合标识
     * @param tag 事件 Tag
     * @return 对应事件；不存在时为空
     */
    Optional<OutboxEvent> findByAggregateIdAndTag(
        @Param("aggregateId") UUID aggregateId,
        @Param("tag") String tag
    );

    /**
     * 插入 Outbox 记录。
     *
     * @param event 待保存事件
     * @return 受影响行数
     */
    int insert(OutboxEvent event);
}

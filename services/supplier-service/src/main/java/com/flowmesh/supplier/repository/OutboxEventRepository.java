package com.flowmesh.supplier.repository;

import com.flowmesh.supplier.domain.OutboxEvent;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * 访问 supplier 服务的 Outbox 事件。
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * 按创建时间读取一批尚未成功投递的事件。
     *
     * @return 最早创建的待投递事件，最多 100 条
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = """
        select * from supplier_outbox_events
        where published_at is null
          and dead_lettered_at is null
          and (next_attempt_at is null or next_attempt_at <= :now)
          and (claimed_until is null or claimed_until < :now)
        order by created_at asc
        limit :limit
        for update skip locked
        """, nativeQuery = true)
    List<OutboxEvent> findAvailableForUpdate(@Param("now") Instant now, @Param("limit") int limit);

    /**
     * 只有持有认领令牌的发布器可以确认发送成功。
     *
     * @return 成功更新的记录数
     */
    @Modifying
    @Transactional
    @Query("""
        update OutboxEvent event
        set event.publishedAt = :publishedAt,
            event.lastError = null,
            event.claimedUntil = null,
            event.claimToken = null
        where event.id = :id and event.claimToken = :claimToken and event.publishedAt is null
        """)
    int markPublishedIfClaimed(@Param("id") UUID id, @Param("claimToken") UUID claimToken,
        @Param("publishedAt") Instant publishedAt);

    /**
     * 记录可重试的投递失败并写入下一次退避时间。
     *
     * @return 成功更新的记录数
     */
    @Modifying
    @Transactional
    @Query("""
        update OutboxEvent event
        set event.attemptCount = event.attemptCount + 1,
            event.lastError = :error,
            event.nextAttemptAt = :nextAttemptAt,
            event.claimedUntil = null,
            event.claimToken = null
        where event.id = :id and event.claimToken = :claimToken
          and event.publishedAt is null and event.attemptCount + 1 < :maxAttempts
        """)
    int recordRetryIfClaimed(@Param("id") UUID id, @Param("claimToken") UUID claimToken,
        @Param("error") String error, @Param("nextAttemptAt") Instant nextAttemptAt,
        @Param("maxAttempts") int maxAttempts);

    /**
     * 将达到最大次数的事件标记为失败终态，供 DLQ/人工重放流程发现。
     *
     * @return 成功更新的记录数
     */
    @Modifying
    @Transactional
    @Query("""
        update OutboxEvent event
        set event.attemptCount = event.attemptCount + 1,
            event.lastError = :error,
            event.deadLetteredAt = :deadLetteredAt,
            event.nextAttemptAt = null,
            event.claimedUntil = null,
            event.claimToken = null
        where event.id = :id and event.claimToken = :claimToken
          and event.publishedAt is null and event.attemptCount + 1 >= :maxAttempts
        """)
    int markDeadLetteredIfClaimed(@Param("id") UUID id, @Param("claimToken") UUID claimToken,
        @Param("error") String error, @Param("deadLetteredAt") Instant deadLetteredAt,
        @Param("maxAttempts") int maxAttempts);

    /**
     * 按业务聚合读取其 Outbox 事件。
     *
     * @param aggregateId 业务聚合标识
     * @return 对应事件
     */
    Optional<OutboxEvent> findByAggregateIdAndTag(UUID aggregateId, String tag);
}

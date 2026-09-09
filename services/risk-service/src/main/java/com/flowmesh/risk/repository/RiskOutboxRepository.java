package com.flowmesh.risk.repository;

import com.flowmesh.risk.domain.RiskOutboxEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 风控结果 Outbox 仓储。
 */
@Mapper
public interface RiskOutboxRepository {

    /**
     * 保存 Outbox 事件。
     *
     * @param event Outbox 事件
     * @return 受影响行数
     */
    int insert(RiskOutboxEvent event);

    /**
     * 认领一批可发布事件。
     *
     * @param limit 批大小
     * @param now 当前时间
     * @param claimedUntil 租约截止时间
     * @param claimToken 租约标识
     * @return 已认领事件
     */
    List<RiskOutboxEvent> claimBatch(
        @Param("limit") int limit,
        @Param("now") Instant now,
        @Param("claimedUntil") Instant claimedUntil,
        @Param("claimToken") UUID claimToken
    );

    /**
     * ACK 后标记已发布。
     *
     * @param id 事件标识
     * @param claimToken 租约标识
     * @return 受影响行数
     */
    int markPublished(@Param("id") UUID id, @Param("claimToken") UUID claimToken);

    /**
     * 记录发布失败和下一次重试时间。
     *
     * @param id 事件标识
     * @param claimToken 租约标识
     * @param attemptCount 新尝试次数
     * @param nextAttemptAt 下次尝试时间
     * @param lastError 错误摘要
     * @param deadLetteredAt 死信时间，可为空
     * @return 受影响行数
     */
    int markFailed(
        @Param("id") UUID id,
        @Param("claimToken") UUID claimToken,
        @Param("attemptCount") int attemptCount,
        @Param("nextAttemptAt") Instant nextAttemptAt,
        @Param("lastError") String lastError,
        @Param("deadLetteredAt") Instant deadLetteredAt
    );
}

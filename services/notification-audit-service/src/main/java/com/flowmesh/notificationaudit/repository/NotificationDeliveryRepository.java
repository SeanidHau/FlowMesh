package com.flowmesh.notificationaudit.repository;

import com.flowmesh.notificationaudit.domain.NotificationDelivery;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 外部通知投递队列仓储。
 */
@Mapper
public interface NotificationDeliveryRepository {

    /**
     * 保存一条与站内通知同事务提交的投递记录。
     *
     * @param delivery 投递记录
     * @return 受影响行数
     */
    int insert(NotificationDelivery delivery);

    /**
     * 认领一批到期的待投递记录。
     *
     * @param now 当前时间
     * @param claimToken 本次认领令牌
     * @param claimedUntil 租约截止时间
     * @param limit 批量上限
     * @return 已认领记录
     */
    List<NotificationDelivery> claimBatch(
        @Param("now") Instant now,
        @Param("claimToken") UUID claimToken,
        @Param("claimedUntil") Instant claimedUntil,
        @Param("limit") int limit
    );

    /**
     * 在发送单条通知前续租，只有原租约仍有效且令牌匹配时才更新。
     *
     * @param id 投递标识
     * @param claimToken 认领令牌
     * @param claimedUntil 新的租约截止时间
     * @return 受影响行数；为 0 时表示租约已失效或已被其他实例接管
     */
    int renewClaim(
        @Param("id") UUID id,
        @Param("claimToken") UUID claimToken,
        @Param("claimedUntil") Instant claimedUntil
    );

    /**
     * 更新成功投递状态，并校验认领令牌避免旧实例覆盖新实例。
     *
     * @param id 投递标识
     * @param claimToken 认领令牌
     * @param deliveredAt 完成时间
     * @return 受影响行数
     */
    int markDelivered(@Param("id") UUID id, @Param("claimToken") UUID claimToken,
                      @Param("deliveredAt") Instant deliveredAt);

    /**
     * 记录失败、安排重试或进入死信。
     *
     * @param id 投递标识
     * @param claimToken 认领令牌
     * @param attempts 累计尝试次数
     * @param nextAttemptAt 下次尝试时间
     * @param status 新状态
     * @param lastError 脱敏后的错误摘要
     * @return 受影响行数
     */
    int markFailed(
        @Param("id") UUID id,
        @Param("claimToken") UUID claimToken,
        @Param("attempts") int attempts,
        @Param("nextAttemptAt") Instant nextAttemptAt,
        @Param("status") String status,
        @Param("lastError") String lastError
    );

    /**
     * 返回待投递记录数量，供 Prometheus 观测。
     *
     * @return 待投递数量
     */
    long countPending();

    /**
     * 返回死信数量，供 Prometheus 观测。
     *
     * @return 死信数量
     */
    long countDeadLettered();
}

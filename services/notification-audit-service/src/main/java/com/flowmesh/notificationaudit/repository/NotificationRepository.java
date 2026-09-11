package com.flowmesh.notificationaudit.repository;

import com.flowmesh.notificationaudit.domain.Notification;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 站内通知仓储。
 */
@Mapper
public interface NotificationRepository {

    /**
     * 保存通知。
     *
     * @param notification 通知记录
     * @return 受影响行数
     */
    int insert(Notification notification);

    /**
     * 查询当前租户下指定用户的通知。
     *
     * @param tenantId 租户标识
     * @param recipientUserId 接收人标识
     * @param limit 最大返回数量
     * @return 通知列表
     */
    List<Notification> findForUser(
        @Param("tenantId") String tenantId,
        @Param("recipientUserId") UUID recipientUserId,
        @Param("limit") int limit
    );

    /**
     * 将属于当前租户和用户的通知标记为已读。
     *
     * @param tenantId 租户标识
     * @param recipientUserId 接收人标识
     * @param notificationId 通知标识
     * @param readAt 已读时间
     * @return 受影响行数
     */
    int markAsRead(
        @Param("tenantId") String tenantId,
        @Param("recipientUserId") UUID recipientUserId,
        @Param("notificationId") UUID notificationId,
        @Param("readAt") Instant readAt
    );
}

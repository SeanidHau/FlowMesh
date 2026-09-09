package com.flowmesh.notificationaudit.api;

import com.flowmesh.notificationaudit.domain.Notification;
import java.time.Instant;
import java.util.UUID;

/**
 * 站内通知响应模型。
 *
 * @param id 通知标识
 * @param type 通知类型
 * @param title 标题
 * @param content 内容
 * @param status 阅读状态
 * @param createdAt 创建时间
 */
public record NotificationResponse(
    UUID id,
    String type,
    String title,
    String content,
    String status,
    Instant createdAt
) {

    /**
     * 将持久化通知转换为 API 响应。
     *
     * @param notification 通知记录
     * @return API 响应
     */
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
            notification.getId(),
            notification.getNotificationType(),
            notification.getTitle(),
            notification.getContent(),
            notification.getStatus(),
            notification.getCreatedAt()
        );
    }
}

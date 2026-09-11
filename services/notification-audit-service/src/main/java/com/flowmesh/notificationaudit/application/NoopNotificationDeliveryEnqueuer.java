package com.flowmesh.notificationaudit.application;

import com.flowmesh.notificationaudit.domain.Notification;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 外部通知关闭时的空实现，避免本地开发和只验证站内通知时产生无意义队列数据。
 */
@Component
@ConditionalOnProperty(
    name = "flowmesh.notification.delivery.enabled",
    havingValue = "false",
    matchIfMissing = true
)
public class NoopNotificationDeliveryEnqueuer implements NotificationDeliveryEnqueuer {

    /**
     * 外部通知关闭时不创建投递记录。
     *
     * @param notification 已落库的站内通知
     */
    @Override
    public void enqueue(Notification notification) {
        // 开关关闭时保持数据库无外部投递副作用。
    }
}

package com.flowmesh.notificationaudit.application;

import com.flowmesh.notificationaudit.domain.Notification;
import com.flowmesh.notificationaudit.domain.NotificationDelivery;
import com.flowmesh.notificationaudit.repository.NotificationDeliveryRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 在当前通知投影事务中写入外部通知投递队列。
 */
@Component
@ConditionalOnProperty(name = "flowmesh.notification.delivery.enabled", havingValue = "true")
public class TransactionalNotificationDeliveryEnqueuer implements NotificationDeliveryEnqueuer {

    private final NotificationDeliveryRepository repository;

    /**
     * 创建事务性投递入队器。
     *
     * @param repository 投递队列仓储
     */
    public TransactionalNotificationDeliveryEnqueuer(NotificationDeliveryRepository repository) {
        this.repository = repository;
    }

    /**
     * 写入投递队列，调用方与站内通知共享同一个事务。
     *
     * @param notification 已落库的站内通知
     */
    @Override
    public void enqueue(Notification notification) {
        repository.insert(NotificationDelivery.pending(notification));
    }
}

package com.flowmesh.notificationaudit.application;

import com.flowmesh.notificationaudit.domain.Notification;

/**
 * 将站内通知投递到可重试的外部通知队列。
 */
public interface NotificationDeliveryEnqueuer {

    /**
     * 创建外部通知投递记录。
     *
     * @param notification 已落库的站内通知
     */
    void enqueue(Notification notification);
}

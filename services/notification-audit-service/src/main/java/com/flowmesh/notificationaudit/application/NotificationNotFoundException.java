package com.flowmesh.notificationaudit.application;

import java.util.UUID;

/**
 * 当前租户或用户无法访问指定通知时抛出的业务异常。
 */
public class NotificationNotFoundException extends RuntimeException {

    /**
     * 创建通知不存在异常。
     *
     * @param notificationId 通知标识
     */
    public NotificationNotFoundException(UUID notificationId) {
        super("通知不存在或不属于当前用户：" + notificationId);
    }
}

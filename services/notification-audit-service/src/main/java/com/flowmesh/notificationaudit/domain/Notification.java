package com.flowmesh.notificationaudit.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 面向申请人的站内通知记录。
 */
public class Notification {

    private UUID id;
    private UUID sourceEventId;
    private String tenantId;
    private UUID recipientUserId;
    private String notificationType;
    private String title;
    private String content;
    private String status;
    private Instant createdAt;
    private Instant readAt;

    /**
     * 供 MyBatis 重建查询结果。
     */
    protected Notification() {
    }

    /**
     * 创建未读通知。
     *
     * @param sourceEventId 来源事件标识
     * @param tenantId 租户标识
     * @param recipientUserId 接收人标识
     * @param notificationType 通知类型
     * @param title 通知标题
     * @param content 通知内容
     * @return 通知记录
     */
    public static Notification unread(
        UUID sourceEventId,
        String tenantId,
        UUID recipientUserId,
        String notificationType,
        String title,
        String content
    ) {
        Notification notification = new Notification();
        notification.id = UUID.randomUUID();
        notification.sourceEventId = sourceEventId;
        notification.tenantId = tenantId;
        notification.recipientUserId = recipientUserId;
        notification.notificationType = notificationType;
        notification.title = title;
        notification.content = content;
        notification.status = "UNREAD";
        notification.createdAt = Instant.now();
        return notification;
    }

    public UUID getId() { return id; }
    public UUID getSourceEventId() { return sourceEventId; }
    public String getTenantId() { return tenantId; }
    public UUID getRecipientUserId() { return recipientUserId; }
    public String getNotificationType() { return notificationType; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReadAt() { return readAt; }

    public void setId(UUID id) { this.id = id; }
    public void setSourceEventId(UUID sourceEventId) { this.sourceEventId = sourceEventId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public void setRecipientUserId(UUID recipientUserId) { this.recipientUserId = recipientUserId; }
    public void setNotificationType(String notificationType) { this.notificationType = notificationType; }
    public void setTitle(String title) { this.title = title; }
    public void setContent(String content) { this.content = content; }
    public void setStatus(String status) { this.status = status; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setReadAt(Instant readAt) { this.readAt = readAt; }
}

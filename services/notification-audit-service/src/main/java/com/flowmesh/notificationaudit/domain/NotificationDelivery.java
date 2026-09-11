package com.flowmesh.notificationaudit.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 外部通知投递队列中的一条可靠投递记录。
 */
public class NotificationDelivery {

    private UUID id;
    private UUID notificationId;
    private UUID sourceEventId;
    private String tenantId;
    private UUID recipientUserId;
    private String notificationType;
    private String title;
    private String content;
    private String status;
    private int attempts;
    private Instant nextAttemptAt;
    private Instant claimedUntil;
    private UUID claimToken;
    private Instant createdAt;

    /**
     * 供 MyBatis 重建查询结果。
     */
    protected NotificationDelivery() {
    }

    /**
     * 根据站内通知创建待投递记录。
     *
     * @param notification 站内通知
     * @return 待投递记录
     */
    public static NotificationDelivery pending(Notification notification) {
        NotificationDelivery delivery = new NotificationDelivery();
        delivery.id = UUID.randomUUID();
        delivery.notificationId = notification.getId();
        delivery.sourceEventId = notification.getSourceEventId();
        delivery.tenantId = notification.getTenantId();
        delivery.recipientUserId = notification.getRecipientUserId();
        delivery.notificationType = notification.getNotificationType();
        delivery.title = notification.getTitle();
        delivery.content = notification.getContent();
        delivery.status = "PENDING";
        delivery.nextAttemptAt = notification.getCreatedAt();
        delivery.createdAt = notification.getCreatedAt();
        return delivery;
    }

    public UUID getId() { return id; }
    public UUID getNotificationId() { return notificationId; }
    public UUID getSourceEventId() { return sourceEventId; }
    public String getTenantId() { return tenantId; }
    public UUID getRecipientUserId() { return recipientUserId; }
    public String getNotificationType() { return notificationType; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getClaimedUntil() { return claimedUntil; }
    public UUID getClaimToken() { return claimToken; }
    public Instant getCreatedAt() { return createdAt; }

    public void setId(UUID id) { this.id = id; }
    public void setNotificationId(UUID notificationId) { this.notificationId = notificationId; }
    public void setSourceEventId(UUID sourceEventId) { this.sourceEventId = sourceEventId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public void setRecipientUserId(UUID recipientUserId) { this.recipientUserId = recipientUserId; }
    public void setNotificationType(String notificationType) { this.notificationType = notificationType; }
    public void setTitle(String title) { this.title = title; }
    public void setContent(String content) { this.content = content; }
    public void setStatus(String status) { this.status = status; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public void setNextAttemptAt(Instant nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }
    public void setClaimedUntil(Instant claimedUntil) { this.claimedUntil = claimedUntil; }
    public void setClaimToken(UUID claimToken) { this.claimToken = claimToken; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

package com.flowmesh.workflow.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 已处理补件提交事件的幂等记录。
 */
public class WorkflowSupplementEventInbox {

    private final UUID eventId;
    private final String tenantId;
    private final UUID applicationId;
    private final Instant createdAt;

    /**
     * 创建补件事件幂等记录。
     *
     * @param eventId 事件标识
     * @param tenantId 租户标识
     * @param applicationId 申请标识
     */
    public WorkflowSupplementEventInbox(UUID eventId, String tenantId, UUID applicationId) {
        this.eventId = Objects.requireNonNull(eventId);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.applicationId = Objects.requireNonNull(applicationId);
        this.createdAt = Instant.now();
    }

    /** @return 事件标识 */
    public UUID getEventId() { return eventId; }

    /** @return 租户标识 */
    public String getTenantId() { return tenantId; }

    /** @return 申请标识 */
    public UUID getApplicationId() { return applicationId; }

    /** @return 创建时间 */
    public Instant getCreatedAt() { return createdAt; }
}

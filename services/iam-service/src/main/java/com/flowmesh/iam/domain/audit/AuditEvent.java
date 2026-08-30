package com.flowmesh.iam.domain.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * IAM 安全审计事件。
 *
 * <p>审计记录只追加，不保存密码、Access Token 或 Refresh Token 原文。</p>
 */
@Entity
@Table(name = "iam_audit_events")
public class AuditEvent {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false, length = 64)
    private String tenantId;

    @Column(name = "actor_user_id", updatable = false)
    private UUID actorUserId;

    @Column(nullable = false, updatable = false, length = 64)
    private String action;

    @Column(name = "target_type", nullable = false, updatable = false, length = 64)
    private String targetType;

    @Column(name = "target_id", updatable = false, length = 128)
    private String targetId;

    @Column(nullable = false, updatable = false, length = 32)
    private String result;

    @Column(name = "trace_id", nullable = false, updatable = false, length = 128)
    private String traceId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    /**
     * 供 JPA 重建实体状态使用。
     */
    protected AuditEvent() {
    }

    /**
     * 创建安全审计事件。
     *
     * @param tenantId 租户标识
     * @param actorUserId 操作者用户标识；认证失败时为空
     * @param action 安全动作
     * @param targetType 目标类型
     * @param targetId 目标标识
     * @param result 动作结果
     * @param traceId 链路追踪标识
     */
    public AuditEvent(
        String tenantId,
        UUID actorUserId,
        String action,
        String targetType,
        String targetId,
        String result,
        String traceId
    ) {
        this.id = UUID.randomUUID();
        this.tenantId = Objects.requireNonNull(tenantId);
        this.actorUserId = actorUserId;
        this.action = Objects.requireNonNull(action);
        this.targetType = Objects.requireNonNull(targetType);
        this.targetId = targetId;
        this.result = Objects.requireNonNull(result);
        this.traceId = Objects.requireNonNull(traceId);
        this.occurredAt = Instant.now();
    }
}

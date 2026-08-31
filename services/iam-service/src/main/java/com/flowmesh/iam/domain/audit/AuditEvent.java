package com.flowmesh.iam.domain.audit;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * IAM 安全审计事件。
 *
 * <p>审计记录只追加，不保存密码、Access Token 或 Refresh Token 原文。</p>
 */
public class AuditEvent {

    private UUID id;

    private String tenantId;

    private UUID actorUserId;

    private String action;

    private String targetType;

    private String targetId;

    private String result;

    private String traceId;

    private Instant occurredAt;

    /**
     * 供 MyBatis 重建持久化对象状态使用。
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

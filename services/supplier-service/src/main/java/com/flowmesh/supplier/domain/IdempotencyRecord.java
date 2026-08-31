package com.flowmesh.supplier.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 请求幂等记录，持久化首次响应快照。
 *
 * <p>同一 (tenant_id, user_id, idempotency_key) 的重复请求回放首次响应。
 * request_fingerprint 用于判断键用途冲突。</p>
 */
public class IdempotencyRecord {

    private UUID id;

    private String tenantId;

    private UUID userId;

    private String idempotencyKey;

    private String requestFingerprint;

    private int responseStatus;

    private String responseBody;

    private Instant createdAt;

    /**
     * 供 MyBatis 重建持久化对象状态使用。
     */
    protected IdempotencyRecord() {
    }

    /**
     * 创建幂等记录。
     *
     * @param tenantId 租户标识
     * @param userId 用户标识
     * @param idempotencyKey 幂等键
     * @param requestFingerprint 请求体 SHA-256 hex
     * @param responseStatus 首次响应 HTTP 状态码
     * @param responseBody 首次响应 JSON 快照
     */
    public IdempotencyRecord(
        String tenantId,
        UUID userId,
        String idempotencyKey,
        String requestFingerprint,
        int responseStatus,
        String responseBody
    ) {
        this.id = UUID.randomUUID();
        this.tenantId = Objects.requireNonNull(tenantId);
        this.userId = Objects.requireNonNull(userId);
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey);
        this.requestFingerprint = Objects.requireNonNull(requestFingerprint);
        this.responseStatus = responseStatus;
        this.responseBody = Objects.requireNonNull(responseBody);
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public int getResponseStatus() {
        return responseStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

}

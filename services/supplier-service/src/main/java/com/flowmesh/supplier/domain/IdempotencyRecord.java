package com.flowmesh.supplier.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 请求幂等记录，持久化首次响应快照。
 *
 * <p>同一 (tenant_id, user_id, idempotency_key) 的重复请求回放首次响应。
 * request_fingerprint 用于判断键用途冲突。</p>
 */
@Entity
@Table(name = "supplier_idempotency_keys")
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false, length = 64)
    private String tenantId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, updatable = false, length = 64)
    private String requestFingerprint;

    @Column(name = "response_status", nullable = false, updatable = false)
    private int responseStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body", nullable = false, updatable = false, columnDefinition = "jsonb")
    private String responseBody;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * 供 JPA 重建实体状态使用。
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
        this.tenantId = Objects.requireNonNull(tenantId);
        this.userId = Objects.requireNonNull(userId);
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey);
        this.requestFingerprint = Objects.requireNonNull(requestFingerprint);
        this.responseStatus = responseStatus;
        this.responseBody = Objects.requireNonNull(responseBody);
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

    @PrePersist
    private void onCreate() {
        this.createdAt = Instant.now();
    }
}

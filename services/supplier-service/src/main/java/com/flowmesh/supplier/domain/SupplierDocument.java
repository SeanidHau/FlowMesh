package com.flowmesh.supplier.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 供应商申请材料元数据。
 *
 * <p>文件内容存储在对象存储，数据库只保存租户隔离所需的元数据和对象键。</p>
 */
public class SupplierDocument {

    private UUID id;
    private String tenantId;
    private UUID applicationId;
    private UUID uploadedBy;
    private String objectKey;
    private String originalFilename;
    private String contentType;
    private long sizeBytes;
    private String sha256;
    private String scanStatus;
    private Instant createdAt;

    /**
     * 供 MyBatis 重建对象使用。
     */
    protected SupplierDocument() {
    }

    /**
     * 创建材料元数据。
     *
     * @param tenantId 租户标识
     * @param applicationId 申请标识
     * @param uploadedBy 上传人
     * @param objectKey 对象键
     * @param originalFilename 原始文件名
     * @param contentType MIME 类型
     * @param sizeBytes 文件大小
     * @param sha256 内容摘要
     * @param scanStatus 扫描状态
     */
    public SupplierDocument(
        String tenantId,
        UUID applicationId,
        UUID uploadedBy,
        String objectKey,
        String originalFilename,
        String contentType,
        long sizeBytes,
        String sha256,
        String scanStatus
    ) {
        this.id = UUID.randomUUID();
        this.tenantId = Objects.requireNonNull(tenantId);
        this.applicationId = Objects.requireNonNull(applicationId);
        this.uploadedBy = Objects.requireNonNull(uploadedBy);
        this.objectKey = Objects.requireNonNull(objectKey);
        this.originalFilename = Objects.requireNonNull(originalFilename);
        this.contentType = Objects.requireNonNull(contentType);
        this.sizeBytes = sizeBytes;
        this.sha256 = Objects.requireNonNull(sha256);
        this.scanStatus = Objects.requireNonNull(scanStatus);
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }

    public String getTenantId() { return tenantId; }

    public UUID getApplicationId() { return applicationId; }

    public UUID getUploadedBy() { return uploadedBy; }

    public String getObjectKey() { return objectKey; }

    public String getOriginalFilename() { return originalFilename; }

    public String getContentType() { return contentType; }

    public long getSizeBytes() { return sizeBytes; }

    public String getSha256() { return sha256; }

    public String getScanStatus() { return scanStatus; }

    public Instant getCreatedAt() { return createdAt; }
}

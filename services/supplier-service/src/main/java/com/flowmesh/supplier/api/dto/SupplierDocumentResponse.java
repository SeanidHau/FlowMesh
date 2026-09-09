package com.flowmesh.supplier.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * 供应商材料元数据响应。
 *
 * @param id 材料标识
 * @param originalFilename 原始文件名
 * @param contentType MIME 类型
 * @param sizeBytes 文件大小
 * @param sha256 内容 SHA-256
 * @param scanStatus 安全扫描状态
 * @param createdAt 上传时间
 */
public record SupplierDocumentResponse(
    UUID id,
    String originalFilename,
    String contentType,
    long sizeBytes,
    String sha256,
    String scanStatus,
    Instant createdAt
) {
}

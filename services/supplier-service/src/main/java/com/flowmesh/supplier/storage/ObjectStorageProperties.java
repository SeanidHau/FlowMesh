package com.flowmesh.supplier.storage;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * S3 兼容对象存储配置。
 *
 * @param endpoint 对象存储服务地址
 * @param accessKey 访问账号
 * @param secretKey 访问密钥
 * @param bucket 材料对象桶名称
 * @param presignedUrlExpirySeconds 下载 URL 有效期，单位为秒
 * @param maxFileSizeBytes 单个文件最大字节数
 * @param readinessEnabled 是否将对象存储连通性纳入 readiness
 */
@ConfigurationProperties(prefix = "flowmesh.object-storage")
public record ObjectStorageProperties(
    String endpoint,
    String accessKey,
    String secretKey,
    String bucket,
    int presignedUrlExpirySeconds,
    long maxFileSizeBytes,
    boolean readinessEnabled
) {

    /** 材料单文件大小上限，与 API 和 Servlet multipart 限制保持一致。 */
    private static final long MAX_SUPPORTED_FILE_SIZE_BYTES = 20L * 1024 * 1024;

    /**
     * 校验对象存储配置，避免上传请求首次执行时才暴露配置错误。
     */
    public ObjectStorageProperties {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("flowmesh.object-storage.endpoint must not be blank");
        }
        URI endpointUri;
        try {
            endpointUri = URI.create(endpoint.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                "flowmesh.object-storage.endpoint must be a valid HTTP(S) URL", exception
            );
        }
        if (!("http".equalsIgnoreCase(endpointUri.getScheme())
            || "https".equalsIgnoreCase(endpointUri.getScheme()))
            || endpointUri.getHost() == null) {
            throw new IllegalArgumentException(
                "flowmesh.object-storage.endpoint must be a valid HTTP(S) URL"
            );
        }
        if (accessKey == null || accessKey.isBlank()) {
            throw new IllegalArgumentException("flowmesh.object-storage.access-key must not be blank");
        }
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalArgumentException("flowmesh.object-storage.secret-key must not be blank");
        }
        if (bucket == null || !bucket.matches("[a-z0-9](?:[a-z0-9.-]{1,61}[a-z0-9])")) {
            throw new IllegalArgumentException(
                "flowmesh.object-storage.bucket must be a lowercase DNS-compatible bucket name"
            );
        }
        if (presignedUrlExpirySeconds < 1 || presignedUrlExpirySeconds > 3600) {
            throw new IllegalArgumentException(
                "flowmesh.object-storage.presigned-url-expiry-seconds must be between 1 and 3600"
            );
        }
        if (maxFileSizeBytes < 1 || maxFileSizeBytes > MAX_SUPPORTED_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException(
                "flowmesh.object-storage.max-file-size-bytes must be between 1 and 20971520"
            );
        }
    }
}

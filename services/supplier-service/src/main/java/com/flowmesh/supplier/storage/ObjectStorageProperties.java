package com.flowmesh.supplier.storage;

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
}

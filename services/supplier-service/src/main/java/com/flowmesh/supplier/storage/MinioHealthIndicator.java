package com.flowmesh.supplier.storage;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * 检查材料对象存储是否可用。
 *
 * <p>本地只运行业务服务时可以关闭该 readiness 检查；生产环境应开启，避免
 * MinIO 不可用时仍向上传材料的请求放流量。</p>
 */
@Component("objectStorage")
public class MinioHealthIndicator implements HealthIndicator {

    private final MinioClient minioClient;
    private final ObjectStorageProperties properties;

    /**
     * 创建对象存储健康检查器。
     *
     * @param minioClient MinIO 客户端
     * @param properties 对象存储配置
     */
    public MinioHealthIndicator(MinioClient minioClient, ObjectStorageProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    /**
     * 检查材料桶是否存在且对象存储可访问。
     *
     * @return 对象存储健康状态
     */
    @Override
    public Health health() {
        if (!properties.readinessEnabled()) {
            return Health.up().withDetail("enabled", false).build();
        }
        try {
            boolean bucketExists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(properties.bucket()).build()
            );
            if (!bucketExists) {
                return Health.down().withDetail("reason", "bucket_not_found").build();
            }
            return Health.up().withDetail("bucket", properties.bucket()).build();
        } catch (Exception exception) {
            return Health.down(exception).withDetail("bucket", properties.bucket()).build();
        }
    }
}

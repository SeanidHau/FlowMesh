package com.flowmesh.supplier.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

/**
 * 验证 MinIO readiness 检查的开关和桶存在性判断。
 */
class MinioHealthIndicatorTest {

    /**
     * 验证关闭 readiness 检查时不会阻塞本地服务启动。
     */
    @Test
    void shouldStayUpWhenReadinessCheckIsDisabled() {
        ObjectStorageProperties properties = properties(false);
        MinioHealthIndicator indicator = new MinioHealthIndicator(mock(MinioClient.class), properties);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
        assertThat(indicator.health().getDetails()).containsEntry("enabled", false);
    }

    /**
     * 验证启用 readiness 检查且材料桶存在时返回健康状态。
     */
    @Test
    void shouldBeUpWhenBucketExists() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        MinioHealthIndicator indicator = new MinioHealthIndicator(minioClient, properties(true));

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
    }

    /**
     * 验证启用 readiness 检查但材料桶不存在时阻止流量进入。
     */
    @Test
    void shouldBeDownWhenBucketDoesNotExist() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);
        MinioHealthIndicator indicator = new MinioHealthIndicator(minioClient, properties(true));

        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
        assertThat(indicator.health().getDetails()).containsEntry("reason", "bucket_not_found");
    }

    private ObjectStorageProperties properties(boolean readinessEnabled) {
        return new ObjectStorageProperties(
            "http://localhost:9000",
            "access-key",
            "secret-key",
            "flowmesh-documents",
            300,
            20 * 1024 * 1024,
            readinessEnabled
        );
    }
}

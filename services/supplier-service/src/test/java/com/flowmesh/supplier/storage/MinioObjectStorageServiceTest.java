package com.flowmesh.supplier.storage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;

/**
 * 验证材料桶自动创建策略，确保生产运行账号不会隐式执行建桶操作。
 */
class MinioObjectStorageServiceTest {

    /**
     * 验证关闭自动建桶时上传路径不调用桶管理 API。
     */
    @Test
    void shouldSkipBucketManagementWhenAutoCreateIsDisabled() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        MinioObjectStorageService service = new MinioObjectStorageService(
            minioClient, properties(false)
        );

        service.put(
            "tenant/application/document", new ByteArrayInputStream(new byte[] {1}), 1, "text/plain"
        );
        verify(minioClient, never()).bucketExists(any(BucketExistsArgs.class));
        verify(minioClient, never()).makeBucket(any(MakeBucketArgs.class));
    }

    /**
     * 验证本地开发开启自动建桶时，目标桶不存在会被创建后继续上传。
     */
    @Test
    void shouldCreateMissingBucketWhenAutoCreateIsEnabled() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);
        MinioObjectStorageService service = new MinioObjectStorageService(
            minioClient, properties(true)
        );

        service.put(
            "tenant/application/document", new ByteArrayInputStream(new byte[] {1}), 1, "text/plain"
        );

        verify(minioClient).makeBucket(any(MakeBucketArgs.class));
    }

    private ObjectStorageProperties properties(boolean autoCreateBucket) {
        return new ObjectStorageProperties(
            "http://localhost:9000",
            "access-key",
            "secret-key",
            "flowmesh-documents",
            autoCreateBucket,
            300,
            20 * 1024 * 1024,
            false
        );
    }
}

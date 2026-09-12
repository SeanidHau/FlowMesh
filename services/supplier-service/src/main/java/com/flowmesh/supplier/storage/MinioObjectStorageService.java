package com.flowmesh.supplier.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 基于 MinIO 的 S3 兼容对象存储实现。
 */
@Service
public class MinioObjectStorageService implements ObjectStorageService {

    private static final Logger log = LoggerFactory.getLogger(MinioObjectStorageService.class);

    private final MinioClient minioClient;
    private final ObjectStorageProperties properties;

    /**
     * 创建对象存储服务。
     *
     * @param minioClient MinIO 客户端
     * @param properties 对象存储配置
     */
    public MinioObjectStorageService(MinioClient minioClient, ObjectStorageProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    /**
     * 将文件流写入受配置保护的材料桶。
     *
     * @param objectKey 对象键
     * @param content 文件流
     * @param size 文件大小
     * @param contentType MIME 类型
     */
    @Override
    public void put(String objectKey, InputStream content, long size, String contentType) {
        try {
            ensureBucket();
            minioClient.putObject(PutObjectArgs.builder()
                .bucket(properties.bucket())
                .object(objectKey)
                .stream(content, size, -1)
                .contentType(contentType)
                .build());
        } catch (Exception exception) {
            throw new ObjectStorageException("材料对象写入失败", exception);
        }
    }

    /**
     * 删除对象；补偿删除失败只记录日志，不覆盖原始数据库异常。
     *
     * @param objectKey 对象键
     */
    @Override
    public void delete(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(properties.bucket())
                .object(objectKey)
                .build());
        } catch (Exception exception) {
            log.error("材料对象补偿删除失败，objectKey={}", objectKey, exception);
        }
    }

    /**
     * 创建仅允许读取指定对象的短期 URL。
     *
     * @param objectKey 对象键
     * @return 预签名下载 URL
     */
    @Override
    public String createPresignedDownloadUrl(String objectKey) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.GET)
                .bucket(properties.bucket())
                .object(objectKey)
                .expiry(properties.presignedUrlExpirySeconds(), TimeUnit.SECONDS)
                .build());
        } catch (Exception exception) {
            throw new ObjectStorageException("材料下载地址生成失败", exception);
        }
    }

    private void ensureBucket() throws Exception {
        if (!properties.autoCreateBucket()) {
            return;
        }
        boolean bucketExists = minioClient.bucketExists(
            BucketExistsArgs.builder().bucket(properties.bucket()).build()
        );
        if (!bucketExists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(properties.bucket()).build());
        }
    }
}

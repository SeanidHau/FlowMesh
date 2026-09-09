package com.flowmesh.supplier.storage;

import io.minio.MinioClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 创建对象存储和文件扫描基础设施客户端。
 */
@Configuration
@EnableConfigurationProperties({ObjectStorageProperties.class, FileScanProperties.class})
public class ObjectStorageConfiguration {

    /**
     * 创建 MinIO/S3 兼容客户端。
     *
     * @param properties 对象存储配置
     * @return MinIO 客户端
     */
    @Bean
    public MinioClient minioClient(ObjectStorageProperties properties) {
        return MinioClient.builder()
            .endpoint(properties.endpoint())
            .credentials(properties.accessKey(), properties.secretKey())
            .build();
    }
}

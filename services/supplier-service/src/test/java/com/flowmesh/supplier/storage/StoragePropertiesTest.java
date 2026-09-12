package com.flowmesh.supplier.storage;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * 验证材料存储和文件扫描配置在启动阶段拒绝危险值。
 */
class StoragePropertiesTest {

    /** 验证启用 ClamAV 时拒绝无效端口、空地址和过短超时。 */
    @Test
    void shouldRejectInvalidFileScanConfiguration() {
        assertThatThrownBy(() -> new FileScanProperties(
            true, "", 3310, Duration.ofSeconds(1)
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("host");

        assertThatThrownBy(() -> new FileScanProperties(
            true, "clamav", 0, Duration.ofSeconds(1)
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("port");

        assertThatThrownBy(() -> new FileScanProperties(
            true, "clamav", 3310, Duration.ofMillis(99)
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("timeout");
    }

    /** 验证对象存储拒绝非 HTTP 地址、非法桶名和超出业务边界的参数。 */
    @Test
    void shouldRejectInvalidObjectStorageConfiguration() {
        assertThatThrownBy(() -> new ObjectStorageProperties(
            "ftp://minio:9000", "access", "secret", "flowmesh-documents", true,
            300, 20 * 1024 * 1024, false
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("endpoint");

        assertThatThrownBy(() -> new ObjectStorageProperties(
            "http://minio:9000", "access", "secret", "FlowMesh", true,
            300, 20 * 1024 * 1024, false
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("bucket");

        assertThatThrownBy(() -> new ObjectStorageProperties(
            "http://minio:9000", "access", "secret", "flowmesh-documents", true,
            3601, 20 * 1024 * 1024, false
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("presigned-url");

        assertThatThrownBy(() -> new ObjectStorageProperties(
            "http://minio:9000", "access", "secret", "flowmesh-documents", true,
            300, 20 * 1024 * 1024 + 1L, false
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("max-file-size");
    }
}

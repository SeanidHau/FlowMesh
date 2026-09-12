package com.flowmesh.supplier.storage;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ClamAV 文件安全扫描配置。
 *
 * @param enabled 是否强制执行 ClamAV 扫描
 * @param host ClamAV daemon 地址
 * @param port ClamAV daemon 端口
 * @param timeout 单次扫描超时时间
 */
@ConfigurationProperties(prefix = "flowmesh.file-scan")
public record FileScanProperties(
    boolean enabled,
    String host,
    int port,
    Duration timeout
) {

    /**
     * 在启用 ClamAV 时校验网络和超时参数，避免材料请求进入不可用的扫描链路。
     */
    public FileScanProperties {
        if (enabled) {
            if (host == null || host.isBlank()) {
                throw new IllegalArgumentException("flowmesh.file-scan.host must not be blank when enabled");
            }
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("flowmesh.file-scan.port must be between 1 and 65535");
            }
            if (timeout == null || timeout.compareTo(Duration.ofMillis(100)) < 0
                || timeout.compareTo(Duration.ofSeconds(60)) > 0) {
                throw new IllegalArgumentException(
                    "flowmesh.file-scan.timeout must be between 100ms and 60s"
                );
            }
        }
    }
}

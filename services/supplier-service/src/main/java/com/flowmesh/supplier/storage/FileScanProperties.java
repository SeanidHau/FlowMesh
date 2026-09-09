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
}

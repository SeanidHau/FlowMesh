package com.flowmesh.supplier.storage;

import java.net.InetSocketAddress;
import java.net.Socket;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * 检查 ClamAV 扫描服务是否可连接。
 */
@Component("clamAv")
public class ClamAvHealthIndicator implements HealthIndicator {

    private final FileScanProperties properties;

    /**
     * 创建 ClamAV 健康检查器。
     *
     * @param properties 文件扫描配置
     */
    public ClamAvHealthIndicator(FileScanProperties properties) {
        this.properties = properties;
    }

    /**
     * 检查 ClamAV TCP 端口是否可连接。
     *
     * @return ClamAV 健康状态
     */
    @Override
    public Health health() {
        if (!properties.enabled()) {
            return Health.up().withDetail("enabled", false).build();
        }
        try (Socket socket = new Socket()) {
            int timeoutMillis = Math.toIntExact(properties.timeout().toMillis());
            socket.connect(new InetSocketAddress(properties.host(), properties.port()), timeoutMillis);
            return Health.up().withDetail("host", properties.host()).withDetail("port", properties.port()).build();
        } catch (Exception exception) {
            return Health.down(exception)
                .withDetail("host", properties.host())
                .withDetail("port", properties.port())
                .build();
        }
    }
}

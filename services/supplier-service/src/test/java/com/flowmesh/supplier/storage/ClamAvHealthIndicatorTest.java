package com.flowmesh.supplier.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

/**
 * 验证 ClamAV readiness 检查的开关和 TCP 连通性判断。
 */
class ClamAvHealthIndicatorTest {

    /**
     * 验证关闭文件扫描时不会因为没有启动 ClamAV 而阻塞本地服务。
     */
    @Test
    void shouldStayUpWhenFileScanIsDisabled() {
        ClamAvHealthIndicator indicator = new ClamAvHealthIndicator(
            new FileScanProperties(false, "127.0.0.1", 3310, Duration.ofMillis(100))
        );

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
        assertThat(indicator.health().getDetails()).containsEntry("enabled", false);
    }

    /**
     * 验证启用文件扫描但 ClamAV 端口不可连接时返回不健康状态。
     */
    @Test
    void shouldBeDownWhenClamAvIsUnavailable() throws IOException {
        int unusedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unusedPort = socket.getLocalPort();
        }
        ClamAvHealthIndicator indicator = new ClamAvHealthIndicator(
            new FileScanProperties(true, "127.0.0.1", unusedPort, Duration.ofMillis(100))
        );

        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }
}

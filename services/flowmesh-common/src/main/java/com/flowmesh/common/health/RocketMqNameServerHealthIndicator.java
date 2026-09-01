package com.flowmesh.common.health;

import java.net.InetSocketAddress;
import java.net.Socket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 通过 TCP 连接检查 RocketMQ NameServer 是否可达。
 *
 * <p>该检查只暴露地址和可用状态，不输出认证信息或消息内容，供 readiness 探针使用。</p>
 */
@Component("rocketMq")
@ConditionalOnProperty(name = "rocketmq.name-server")
public class RocketMqNameServerHealthIndicator implements HealthIndicator {

    private final String address;

    /**
     * 创建 NameServer 健康检查器。
     *
     * @param address NameServer 地址，格式为 host:port
     */
    public RocketMqNameServerHealthIndicator(
        @Value("${rocketmq.name-server}") String address
    ) {
        this.address = address.split(",")[0].trim();
    }

    /**
     * 检查 NameServer TCP 端口是否可连接。
     *
     * @return 健康状态
     */
    @Override
    public Health health() {
        String[] parts = address.split(":", 2);
        if (parts.length != 2) {
            return Health.down().withDetail("address", address).withDetail("reason", "invalid address").build();
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(parts[0], Integer.parseInt(parts[1])), 1_000);
            return Health.up().withDetail("address", address).build();
        } catch (Exception exception) {
            return Health.down(exception).withDetail("address", address).build();
        }
    }
}

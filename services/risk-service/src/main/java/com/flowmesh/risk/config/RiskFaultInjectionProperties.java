package com.flowmesh.risk.config;

import java.time.Duration;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 风控服务的受控故障注入配置。
 *
 * <p>该配置只用于隔离的演练环境，默认关闭。开启后，风控消费者会在写入结果前
 * 主动失败，以便验证 RocketMQ 重试和死信处置链路。</p>
 *
 * @param enabled 是否启用故障注入
 * @param mode 故障模式，支持 {@code FAIL} 和 {@code TIMEOUT}
 * @param delay TIMEOUT 模式在抛出异常前等待的时间
 */
@ConfigurationProperties(prefix = "flowmesh.risk.fault-injection")
public record RiskFaultInjectionProperties(
    boolean enabled,
    String mode,
    Duration delay
) {

    /**
     * 校验并返回当前故障模式。
     *
     * @return 大写故障模式
     * @throws IllegalArgumentException 配置模式不受支持或延迟超出边界
     */
    public String validatedMode() {
        String normalizedMode = mode == null ? "FAIL" : mode.trim().toUpperCase(Locale.ROOT);
        if (!"FAIL".equals(normalizedMode) && !"TIMEOUT".equals(normalizedMode)) {
            throw new IllegalArgumentException("flowmesh.risk.fault-injection.mode must be FAIL or TIMEOUT");
        }
        if (delay == null || delay.isNegative() || delay.compareTo(Duration.ofSeconds(60)) > 0) {
            throw new IllegalArgumentException(
                "flowmesh.risk.fault-injection.delay must be between 0s and 60s"
            );
        }
        return normalizedMode;
    }
}

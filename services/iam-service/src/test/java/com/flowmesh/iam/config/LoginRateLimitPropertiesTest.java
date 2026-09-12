package com.flowmesh.iam.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * 验证登录限流配置的启动时约束和重试等待时间计算。
 */
class LoginRateLimitPropertiesTest {

    /** 验证启用限流时拒绝无效的阈值和时间窗口。 */
    @Test
    void shouldRejectInvalidEnabledConfiguration() {
        assertThatThrownBy(() -> new LoginRateLimitProperties(
            true, 0, 30, Duration.ofMinutes(1), false
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("account-max-attempts");

        assertThatThrownBy(() -> new LoginRateLimitProperties(
            true, 5, 0, Duration.ofMinutes(1), false
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("client-max-attempts");

        assertThatThrownBy(() -> new LoginRateLimitProperties(
            true, 5, 30, Duration.ZERO, false
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("window");
    }

    /** 验证 Retry-After 使用配置窗口并对不足一秒的窗口向上取整。 */
    @Test
    void shouldCalculateRetryAfterSecondsFromWindow() {
        assertThat(new LoginRateLimitProperties(
            true, 5, 30, Duration.ofSeconds(90), false
        ).retryAfterSeconds()).isEqualTo(90);
        assertThat(new LoginRateLimitProperties(
            true, 5, 30, Duration.ofMillis(1500), false
        ).retryAfterSeconds()).isEqualTo(2);
    }
}

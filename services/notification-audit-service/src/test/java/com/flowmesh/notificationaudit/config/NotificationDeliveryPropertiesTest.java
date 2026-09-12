package com.flowmesh.notificationaudit.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * 验证外部通知投递配置在服务启动阶段拒绝不安全或不可运行的参数。
 */
class NotificationDeliveryPropertiesTest {

    /**
     * 验证启用投递时拒绝格式错误、非 HTTPS 和携带用户信息的地址。
     */
    @Test
    void shouldRejectInvalidWebhookUrl() {
        assertThatThrownBy(() -> validate("https://", 3000, 1000))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("webhook-url");
        assertThatThrownBy(() -> validate("http://notify.example.test/hook", 3000, 1000))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("webhook-url");
        assertThatThrownBy(() -> validate("https://user:password@notify.example.test/hook", 3000, 1000))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("webhook-url");
    }

    /**
     * 验证发送超时、重试退避和发布间隔不会被配置为过大的阻塞值。
     */
    @Test
    void shouldRejectUnsafeTimingValues() {
        assertThatThrownBy(() -> validate("https://notify.example.test/hook", 60_001, 1000))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("timing");
        assertThatThrownBy(() -> validate("https://notify.example.test/hook", 3000, 60_001))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("timing");
    }

    private void validate(String webhookUrl, long sendTimeoutMillis, long publishIntervalMillis) {
        NotificationDeliveryProperties properties = new NotificationDeliveryProperties();
        properties.setEnabled(true);
        properties.setWebhookUrl(webhookUrl);
        properties.setSigningSecret("01234567890123456789012345678901");
        properties.setBatchSize(10);
        properties.setLeaseSeconds(60);
        properties.setSendTimeoutMillis(sendTimeoutMillis);
        properties.setPublishIntervalMillis(publishIntervalMillis);
        properties.validate();
    }
}

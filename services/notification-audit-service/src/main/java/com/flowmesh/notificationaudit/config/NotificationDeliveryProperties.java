package com.flowmesh.notificationaudit.config;

import java.net.URI;
import java.net.URISyntaxException;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 外部通知投递的运行参数。
 */
@ConfigurationProperties(prefix = "flowmesh.notification.delivery")
public class NotificationDeliveryProperties {

    private boolean enabled;
    private String webhookUrl = "";
    private String signingSecret = "";
    private int batchSize = 10;
    private long leaseSeconds = 60;
    private int maxAttempts = 5;
    private long retryBaseDelaySeconds = 1;
    private long sendTimeoutMillis = 3000;
    private long publishIntervalMillis = 1000;

    /**
     * 启用外部投递时校验安全和调度参数，避免服务启动后才发现无法投递。
     */
    @PostConstruct
    public void validate() {
        if (!enabled) {
            return;
        }
        if (!isValidWebhookUrl(webhookUrl)) {
            throw new IllegalArgumentException(
                "flowmesh.notification.delivery.webhook-url must be a valid HTTPS URL without user info"
            );
        }
        if (signingSecret == null || signingSecret.length() < 32) {
            throw new IllegalArgumentException(
                "flowmesh.notification.delivery.signing-secret must contain at least 32 characters"
            );
        }
        if (batchSize < 1 || batchSize > 100) {
            throw new IllegalArgumentException("flowmesh.notification.delivery.batch-size must be between 1 and 100");
        }
        if (maxAttempts < 1 || maxAttempts > 20) {
            throw new IllegalArgumentException("flowmesh.notification.delivery.max-attempts must be between 1 and 20");
        }
        if (retryBaseDelaySeconds < 1 || retryBaseDelaySeconds > 900
            || sendTimeoutMillis < 100 || sendTimeoutMillis > 60_000
            || publishIntervalMillis < 100 || publishIntervalMillis > 60_000) {
            throw new IllegalArgumentException("notification delivery timing values are outside the safe range");
        }
        long minimumLeaseSeconds = (batchSize * sendTimeoutMillis + 999) / 1000 + 10;
        if (leaseSeconds < minimumLeaseSeconds) {
            throw new IllegalArgumentException(
                "flowmesh.notification.delivery.lease-seconds must cover the batch send timeout plus a safety margin"
            );
        }
    }

    public boolean isEnabled() { return enabled; }
    public String getWebhookUrl() { return webhookUrl; }
    public String getSigningSecret() { return signingSecret; }
    public int getBatchSize() { return batchSize; }
    public long getLeaseSeconds() { return leaseSeconds; }
    public int getMaxAttempts() { return maxAttempts; }
    public long getRetryBaseDelaySeconds() { return retryBaseDelaySeconds; }
    public long getSendTimeoutMillis() { return sendTimeoutMillis; }
    public long getPublishIntervalMillis() { return publishIntervalMillis; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setWebhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; }
    public void setSigningSecret(String signingSecret) { this.signingSecret = signingSecret; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public void setLeaseSeconds(long leaseSeconds) { this.leaseSeconds = leaseSeconds; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public void setRetryBaseDelaySeconds(long retryBaseDelaySeconds) { this.retryBaseDelaySeconds = retryBaseDelaySeconds; }
    public void setSendTimeoutMillis(long sendTimeoutMillis) { this.sendTimeoutMillis = sendTimeoutMillis; }
    public void setPublishIntervalMillis(long publishIntervalMillis) { this.publishIntervalMillis = publishIntervalMillis; }

    private static boolean isValidWebhookUrl(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            URI uri = new URI(value.trim());
            return "https".equalsIgnoreCase(uri.getScheme())
                && uri.getHost() != null
                && uri.getUserInfo() == null
                && uri.getFragment() == null;
        } catch (URISyntaxException exception) {
            return false;
        }
    }
}

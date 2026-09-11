package com.flowmesh.notificationaudit.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flowmesh.notificationaudit.config.NotificationDeliveryProperties;
import com.flowmesh.notificationaudit.domain.NotificationDelivery;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.MediaType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 通过 HMAC-SHA256 签名向外部系统发送通知。
 */
@Component
@ConditionalOnProperty(name = "flowmesh.notification.delivery.enabled", havingValue = "true")
public class NotificationWebhookClient {

    private static final String SIGNATURE_ALGORITHM = "HmacSHA256";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final NotificationDeliveryProperties properties;

    /**
     * 创建 Webhook 客户端。
     *
     * @param restClient 带超时的 HTTP 客户端
     * @param objectMapper JSON 序列化器
     * @param properties 投递配置
     */
    public NotificationWebhookClient(
        RestClient restClient,
        ObjectMapper objectMapper,
        NotificationDeliveryProperties properties
    ) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * 发送一条外部通知。对端应使用 delivery id 或 Idempotency-Key 实现幂等。
     *
     * @param delivery 投递记录
     */
    public void send(NotificationDelivery delivery) {
        String body = serialize(delivery);
        restClient.post()
            .uri(properties.getWebhookUrl())
            .contentType(MediaType.APPLICATION_JSON)
            .header("User-Agent", "FlowMesh-Notification-Audit/1")
            .header("X-FlowMesh-Delivery-Id", delivery.getId().toString())
            .header("X-FlowMesh-Signature", "sha256=" + sign(body))
            .header("Idempotency-Key", delivery.getId().toString())
            .body(body)
            .retrieve()
            .toBodilessEntity();
    }

    private String serialize(NotificationDelivery delivery) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("deliveryId", delivery.getId().toString());
        payload.put("notificationId", delivery.getNotificationId().toString());
        payload.put("sourceEventId", delivery.getSourceEventId().toString());
        payload.put("tenantId", delivery.getTenantId());
        payload.put("recipientUserId", delivery.getRecipientUserId().toString());
        payload.put("notificationType", delivery.getNotificationType());
        payload.put("title", delivery.getTitle());
        payload.put("content", delivery.getContent());
        payload.put("createdAt", delivery.getCreatedAt().toString());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("通知投递载荷序列化失败", exception);
        }
    }

    private String sign(String body) {
        try {
            Mac mac = Mac.getInstance(SIGNATURE_ALGORITHM);
            mac.init(new SecretKeySpec(
                properties.getSigningSecret().getBytes(StandardCharsets.UTF_8), SIGNATURE_ALGORITHM
            ));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("通知投递签名失败", exception);
        }
    }
}

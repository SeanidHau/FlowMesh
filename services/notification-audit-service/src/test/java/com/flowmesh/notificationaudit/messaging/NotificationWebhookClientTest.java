package com.flowmesh.notificationaudit.messaging;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;

import com.flowmesh.notificationaudit.config.NotificationDeliveryProperties;
import com.flowmesh.notificationaudit.domain.Notification;
import com.flowmesh.notificationaudit.domain.NotificationDelivery;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;

/**
 * 验证 Webhook 请求包含幂等标识、签名头和可被接收方校验的 JSON 载荷。
 */
class NotificationWebhookClientTest {

    /**
     * 发送请求时不泄露签名密钥，并携带固定的投递幂等标识。
     */
    @Test
    void shouldSendSignedIdempotentWebhook() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = restClientBuilder.build();
        NotificationDeliveryProperties properties = new NotificationDeliveryProperties();
        properties.setWebhookUrl("https://notify.example.test/hooks/flowmesh");
        properties.setSigningSecret("01234567890123456789012345678901");
        NotificationDelivery delivery = NotificationDelivery.pending(Notification.unread(
            UUID.randomUUID(), "tenant-a", UUID.randomUUID(), "SUPPLIER_ACTIVATED", "title", "content"
        ));

        server.expect(requestTo(properties.getWebhookUrl()))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-FlowMesh-Delivery-Id", delivery.getId().toString()))
            .andExpect(header("Idempotency-Key", delivery.getId().toString()))
            .andExpect(header("X-FlowMesh-Signature", startsWith("sha256=")))
            .andExpect(content().string(allOf(
                containsString("\"tenantId\":\"tenant-a\""),
                containsString("\"content\":\"content\""),
                not(containsString("01234567890123456789012345678901"))
            )))
            .andRespond(withSuccess());

        new NotificationWebhookClient(restClient, new com.fasterxml.jackson.databind.ObjectMapper(), properties)
            .send(delivery);

        server.verify();
    }
}

package com.flowmesh.notificationaudit.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 创建启用外部通知投递时才需要的 HTTP 客户端。
 */
@Configuration
@EnableConfigurationProperties(NotificationDeliveryProperties.class)
@ConditionalOnProperty(name = "flowmesh.notification.delivery.enabled", havingValue = "true")
public class NotificationDeliveryConfiguration {

    /**
     * 创建带连接和读取超时的 JDK HTTP 客户端，避免调度线程无限等待。
     *
     * @param properties 投递配置
     * @return Spring RestClient
     */
    @Bean
    public RestClient notificationWebhookRestClient(NotificationDeliveryProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(properties.getSendTimeoutMillis()))
            .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(properties.getSendTimeoutMillis()));
        return RestClient.builder().requestFactory(requestFactory).build();
    }
}

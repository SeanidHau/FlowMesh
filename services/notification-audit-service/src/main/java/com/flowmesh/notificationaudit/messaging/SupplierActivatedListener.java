package com.flowmesh.notificationaudit.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmesh.notificationaudit.application.NotificationAuditService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 消费供应商启用事件并生成通知与审计投影。
 */
@Component
@ConditionalOnProperty(name = "flowmesh.audit.consumer-enabled", havingValue = "true")
@RocketMQMessageListener(
    topic = "supplier-events",
    selectorExpression = "SupplierActivated",
    consumerGroup = "flowmesh-notification-audit",
    maxReconsumeTimes = 3
)
public class SupplierActivatedListener implements RocketMQListener<String> {

    private final NotificationAuditService service;
    private final ObjectMapper objectMapper;
    private final Counter successCounter;
    private final Counter failureCounter;

    /**
     * 创建供应商启用事件监听器。
     *
     * @param service 通知审计服务
     * @param objectMapper JSON 解析器
     * @param meterRegistry 指标注册器
     */
    public SupplierActivatedListener(
        NotificationAuditService service,
        ObjectMapper objectMapper,
        MeterRegistry meterRegistry
    ) {
        this.service = service;
        this.objectMapper = objectMapper;
        this.successCounter = Counter.builder("flowmesh.messaging.consumed")
            .tag("consumer", "supplier-activated-notification-audit").register(meterRegistry);
        this.failureCounter = Counter.builder("flowmesh.messaging.failed")
            .tag("consumer", "supplier-activated-notification-audit").register(meterRegistry);
    }

    /**
     * 处理供应商启用事件。
     *
     * @param message RocketMQ 消息体
     */
    @Override
    public void onMessage(String message) {
        try (MDC.MDCCloseable ignored = MDC.putCloseable("traceId", traceId(message))) {
            try {
                service.handleSupplierActivated(message);
                successCounter.increment();
            } catch (RuntimeException exception) {
                failureCounter.increment();
                throw exception;
            }
        }
    }

    private String traceId(String message) {
        try {
            return objectMapper.readTree(message).path("traceId").asText("");
        } catch (JsonProcessingException exception) {
            return "";
        }
    }
}

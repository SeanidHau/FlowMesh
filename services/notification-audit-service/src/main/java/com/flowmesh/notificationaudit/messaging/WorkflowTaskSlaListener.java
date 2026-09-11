package com.flowmesh.notificationaudit.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmesh.notificationaudit.application.NotificationAuditService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * 消费 Workflow SLA 催办和升级事件，生成申请人可见的通知与审计记录。
 */
@Component
@ConditionalOnProperty(name = "flowmesh.audit.consumer-enabled", havingValue = "true")
@RocketMQMessageListener(
    topic = "workflow-events",
    selectorExpression = "WorkflowTaskSlaReminderRequested || WorkflowTaskSlaEscalated",
    consumerGroup = "flowmesh-notification-workflow-sla",
    maxReconsumeTimes = 3
)
public class WorkflowTaskSlaListener implements RocketMQListener<String> {

    private final NotificationAuditService service;
    private final ObjectMapper objectMapper;
    private final Counter successCounter;
    private final Counter failureCounter;
    private final Timer processingTimer;

    /**
     * 创建 Workflow SLA 监听器。
     *
     * @param service 通知审计服务
     * @param meterRegistry 指标注册器
     */
    public WorkflowTaskSlaListener(
        NotificationAuditService service,
        ObjectMapper objectMapper,
        MeterRegistry meterRegistry
    ) {
        this.service = service;
        this.objectMapper = objectMapper;
        this.successCounter = Counter.builder("flowmesh.messaging.consumed")
            .tag("consumer", "workflow-task-sla").register(meterRegistry);
        this.failureCounter = Counter.builder("flowmesh.messaging.failed")
            .tag("consumer", "workflow-task-sla").register(meterRegistry);
        this.processingTimer = Timer.builder("flowmesh.messaging.processing")
            .description("RocketMQ 消费消息处理耗时")
            .tag("consumer", "workflow-task-sla")
            .publishPercentileHistogram()
            .register(meterRegistry);
    }

    /**
     * 处理一条 SLA 事件。
     *
     * @param message RocketMQ 消息体
     */
    @Override
    public void onMessage(String message) {
        Timer.Sample sample = Timer.start();
        try (MDC.MDCCloseable ignored = MDC.putCloseable("traceId", traceId(message))) {
            try {
                service.handleWorkflowTaskSla(message);
                successCounter.increment();
            } catch (RuntimeException exception) {
                failureCounter.increment();
                throw exception;
            }
        } finally {
            sample.stop(processingTimer);
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

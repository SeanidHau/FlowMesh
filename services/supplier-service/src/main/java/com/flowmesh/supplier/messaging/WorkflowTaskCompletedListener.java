package com.flowmesh.supplier.messaging;

import com.flowmesh.supplier.application.WorkflowTaskCompletedService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 消费 workflow-service 发布的审批完成事件。
 */
@Component
@ConditionalOnProperty(name = "flowmesh.supplier.consumer.enabled", havingValue = "true")
@RocketMQMessageListener(
    topic = "workflow-events",
    selectorExpression = "WorkflowTaskCompleted",
    consumerGroup = "flowmesh-supplier",
    maxReconsumeTimes = 3
)
public class WorkflowTaskCompletedListener implements RocketMQListener<String> {

    private static final Logger log = LoggerFactory.getLogger(WorkflowTaskCompletedListener.class);

    private final WorkflowTaskCompletedService service;
    private final ObjectMapper objectMapper;
    private final Counter successCounter;
    private final Counter failureCounter;

    /**
     * 创建审批完成事件监听器。
     *
     * @param service 审批结果处理服务
     */
    public WorkflowTaskCompletedListener(
        WorkflowTaskCompletedService service,
        ObjectMapper objectMapper,
        MeterRegistry meterRegistry
    ) {
        this.service = service;
        this.objectMapper = objectMapper;
        this.successCounter = Counter.builder("flowmesh.messaging.consumed")
            .tag("consumer", "supplier-workflow-task-completed").register(meterRegistry);
        this.failureCounter = Counter.builder("flowmesh.messaging.failed")
            .tag("consumer", "supplier-workflow-task-completed").register(meterRegistry);
    }

    /**
     * 处理一条审批完成事件。
     *
     * @param message RocketMQ 消息体
     */
    @Override
    public void onMessage(String message) {
        try {
            service.apply(message);
            successCounter.increment();
            log.info("RocketMQ 消费成功，consumer=supplier-workflow-task-completed，{}", eventContext(message));
        } catch (RuntimeException exception) {
            failureCounter.increment();
            log.warn("RocketMQ 消费失败，consumer=supplier-workflow-task-completed，{}", eventContext(message), exception);
            throw exception;
        }
    }

    private String eventContext(String message) {
        try {
            com.fasterxml.jackson.databind.JsonNode event = objectMapper.readTree(message);
            return "eventId=" + event.path("eventId").asText("unknown")
                + "，tenantId=" + event.path("tenantId").asText("unknown")
                + "，traceId=" + event.path("traceId").asText("unknown");
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            return "eventId=unknown，tenantId=unknown，traceId=unknown";
        }
    }
}

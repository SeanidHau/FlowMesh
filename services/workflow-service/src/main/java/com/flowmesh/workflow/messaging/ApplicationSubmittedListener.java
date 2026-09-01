package com.flowmesh.workflow.messaging;

import com.flowmesh.workflow.application.WorkflowEventProjectionService;
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
 * 消费 supplier-service 发布的供应商申请提交事件。
 */
@Component
@ConditionalOnProperty(name = "flowmesh.workflow.consumer.enabled", havingValue = "true")
@RocketMQMessageListener(
    topic = "supplier-events",
    selectorExpression = "ApplicationSubmitted",
    consumerGroup = "flowmesh-workflow",
    maxReconsumeTimes = 3
)
public class ApplicationSubmittedListener implements RocketMQListener<String> {

    private static final Logger log = LoggerFactory.getLogger(ApplicationSubmittedListener.class);

    private final WorkflowEventProjectionService projectionService;
    private final ObjectMapper objectMapper;
    private final Counter successCounter;
    private final Counter failureCounter;

    /**
     * 创建申请提交事件监听器。
     *
     * @param projectionService 流程投影服务
     */
    public ApplicationSubmittedListener(
        WorkflowEventProjectionService projectionService,
        ObjectMapper objectMapper,
        MeterRegistry meterRegistry
    ) {
        this.projectionService = projectionService;
        this.objectMapper = objectMapper;
        this.successCounter = Counter.builder("flowmesh.messaging.consumed")
            .tag("consumer", "workflow-application-submitted").register(meterRegistry);
        this.failureCounter = Counter.builder("flowmesh.messaging.failed")
            .tag("consumer", "workflow-application-submitted").register(meterRegistry);
    }

    /**
     * 处理一条申请提交事件。
     *
     * @param message RocketMQ 消息体
     */
    @Override
    public void onMessage(String message) {
        try {
            projectionService.project(message);
            successCounter.increment();
            log.info("RocketMQ 消费成功，consumer=workflow-application-submitted，{}", eventContext(message));
        } catch (RuntimeException exception) {
            failureCounter.increment();
            log.warn("RocketMQ 消费失败，consumer=workflow-application-submitted，{}", eventContext(message), exception);
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

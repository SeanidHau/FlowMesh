package com.flowmesh.workflow.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmesh.workflow.application.RiskCheckResultService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 消费 risk-service 发布的风控结果事件。
 */
@Component
@ConditionalOnProperty(name = "flowmesh.workflow.consumer.enabled", havingValue = "true")
@RocketMQMessageListener(
    topic = "risk-events",
    selectorExpression = "RiskCheckCompleted",
    consumerGroup = "flowmesh-workflow-risk",
    maxReconsumeTimes = 3
)
public class RiskCheckCompletedListener implements RocketMQListener<String> {

    private final RiskCheckResultService service;
    private final ObjectMapper objectMapper;
    private final Counter successCounter;
    private final Counter failureCounter;
    private final Timer processingTimer;

    /**
     * 创建风控结果监听器。
     *
     * @param service 风控结果服务
     * @param objectMapper JSON 解析器
     * @param meterRegistry 指标注册器
     */
    public RiskCheckCompletedListener(
        RiskCheckResultService service,
        ObjectMapper objectMapper,
        MeterRegistry meterRegistry
    ) {
        this.service = service;
        this.objectMapper = objectMapper;
        this.successCounter = Counter.builder("flowmesh.messaging.consumed")
            .tag("consumer", "workflow-risk-check-completed").register(meterRegistry);
        this.failureCounter = Counter.builder("flowmesh.messaging.failed")
            .tag("consumer", "workflow-risk-check-completed").register(meterRegistry);
        this.processingTimer = Timer.builder("flowmesh.messaging.processing")
            .description("RocketMQ 消费消息处理耗时")
            .tag("consumer", "workflow-risk-check-completed")
            .publishPercentileHistogram()
            .register(meterRegistry);
    }

    /**
     * 处理风控结果。
     *
     * @param message RocketMQ 消息体
     */
    @Override
    public void onMessage(String message) {
        Timer.Sample sample = Timer.start();
        try (MDC.MDCCloseable ignored = MDC.putCloseable("traceId", traceId(message))) {
            try {
                service.apply(message);
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
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            return "";
        }
    }
}

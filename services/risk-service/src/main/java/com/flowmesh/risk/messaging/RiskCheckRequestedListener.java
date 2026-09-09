package com.flowmesh.risk.messaging;

import com.flowmesh.risk.application.RiskEvaluationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 消费 workflow-service 发布的风控请求。
 */
@Component
@ConditionalOnProperty(name = "flowmesh.risk.consumer-enabled", havingValue = "true")
@RocketMQMessageListener(
    topic = "risk-events",
    selectorExpression = "RiskCheckRequested",
    consumerGroup = "flowmesh-risk",
    maxReconsumeTimes = 3
)
public class RiskCheckRequestedListener implements RocketMQListener<String> {

    private final RiskEvaluationService service;
    private final ObjectMapper objectMapper;
    private final Counter successCounter;
    private final Counter failureCounter;

    /**
     * 创建风控请求监听器。
     *
     * @param service 风控评估服务
     * @param objectMapper JSON 解析器
     * @param meterRegistry 指标注册器
     */
    public RiskCheckRequestedListener(
        RiskEvaluationService service,
        ObjectMapper objectMapper,
        MeterRegistry meterRegistry
    ) {
        this.service = service;
        this.objectMapper = objectMapper;
        this.successCounter = Counter.builder("flowmesh.messaging.consumed")
            .tag("consumer", "risk-check-requested").register(meterRegistry);
        this.failureCounter = Counter.builder("flowmesh.messaging.failed")
            .tag("consumer", "risk-check-requested").register(meterRegistry);
    }

    /**
     * 处理风控请求。
     *
     * @param message RocketMQ 消息体
     */
    @Override
    public void onMessage(String message) {
        try (MDC.MDCCloseable ignored = MDC.putCloseable("traceId", traceId(message))) {
            try {
                service.evaluate(message);
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
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            return "";
        }
    }
}

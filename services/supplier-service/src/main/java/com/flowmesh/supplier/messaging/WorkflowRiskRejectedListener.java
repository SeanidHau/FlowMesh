package com.flowmesh.supplier.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmesh.supplier.application.WorkflowRiskRejectedService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 消费 workflow 风控拒绝事件并更新供应商申请终态。
 */
@Component
@ConditionalOnProperty(name = "flowmesh.supplier.consumer.enabled", havingValue = "true")
@RocketMQMessageListener(
    topic = "workflow-events",
    selectorExpression = "WorkflowRiskRejected",
    consumerGroup = "flowmesh-supplier-risk-rejected",
    maxReconsumeTimes = 3
)
public class WorkflowRiskRejectedListener implements RocketMQListener<String> {

    private final WorkflowRiskRejectedService service;
    private final ObjectMapper objectMapper;
    private final Counter successCounter;
    private final Counter failureCounter;
    private final Timer processingTimer;

    /**
     * 创建风控拒绝事件监听器。
     *
     * @param service 风控拒绝应用服务
     * @param objectMapper JSON 解析器
     * @param meterRegistry 指标注册器
     */
    public WorkflowRiskRejectedListener(
        WorkflowRiskRejectedService service,
        ObjectMapper objectMapper,
        MeterRegistry meterRegistry
    ) {
        this.service = service;
        this.objectMapper = objectMapper;
        this.successCounter = Counter.builder("flowmesh.messaging.consumed")
            .tag("consumer", "supplier-workflow-risk-rejected").register(meterRegistry);
        this.failureCounter = Counter.builder("flowmesh.messaging.failed")
            .tag("consumer", "supplier-workflow-risk-rejected").register(meterRegistry);
        this.processingTimer = Timer.builder("flowmesh.messaging.processing")
            .description("RocketMQ 消费消息处理耗时")
            .tag("consumer", "supplier-workflow-risk-rejected")
            .publishPercentileHistogram()
            .register(meterRegistry);
    }

    /**
     * 处理一条风控拒绝事件。
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
        } catch (JsonProcessingException exception) {
            return "";
        }
    }
}

package com.flowmesh.workflow.messaging;

import com.flowmesh.workflow.application.SupplementSubmittedService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 消费申请人补件提交事件。
 */
@Component
@ConditionalOnProperty(name = "flowmesh.workflow.consumer.enabled", havingValue = "true")
@RocketMQMessageListener(
    topic = "supplier-events",
    selectorExpression = "SupplementSubmitted",
    consumerGroup = "flowmesh-workflow-supplement",
    consumeMode = ConsumeMode.ORDERLY,
    maxReconsumeTimes = 3
)
public class SupplementSubmittedListener implements RocketMQListener<String> {

    private final SupplementSubmittedService service;
    private final Counter successCounter;
    private final Counter failureCounter;
    private final Timer processingTimer;

    /**
     * 创建补件提交监听器。
     *
     * @param service 补件提交服务
     * @param meterRegistry 指标注册器
     */
    public SupplementSubmittedListener(SupplementSubmittedService service, MeterRegistry meterRegistry) {
        this.service = service;
        this.successCounter = Counter.builder("flowmesh.messaging.consumed")
            .tag("consumer", "workflow-supplement-submitted").register(meterRegistry);
        this.failureCounter = Counter.builder("flowmesh.messaging.failed")
            .tag("consumer", "workflow-supplement-submitted").register(meterRegistry);
        this.processingTimer = Timer.builder("flowmesh.messaging.processing")
            .description("RocketMQ 消费消息处理耗时")
            .tag("consumer", "workflow-supplement-submitted")
            .publishPercentileHistogram()
            .register(meterRegistry);
    }

    /**
     * 处理补件提交事件。
     *
     * @param message RocketMQ 消息体
     */
    @Override
    public void onMessage(String message) {
        Timer.Sample sample = Timer.start();
        try {
            service.apply(message);
            successCounter.increment();
        } catch (RuntimeException exception) {
            failureCounter.increment();
            throw exception;
        } finally {
            sample.stop(processingTimer);
        }
    }
}

package com.flowmesh.supplier.messaging;

import com.flowmesh.supplier.application.SupplierSupplementService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 消费 workflow 发出的补件请求。
 */
@Component
@ConditionalOnProperty(name = "flowmesh.supplier.consumer.enabled", havingValue = "true")
@RocketMQMessageListener(
    topic = "workflow-events",
    selectorExpression = "SupplementRequested",
    consumerGroup = "flowmesh-supplier-supplement",
    consumeMode = ConsumeMode.ORDERLY,
    maxReconsumeTimes = 3
)
public class SupplementRequestedListener implements RocketMQListener<String> {

    private final SupplierSupplementService service;
    private final Counter successCounter;
    private final Counter failureCounter;
    private final Timer processingTimer;

    /**
     * 创建补件请求监听器。
     *
     * @param service 补件应用服务
     * @param meterRegistry 指标注册器
     */
    public SupplementRequestedListener(SupplierSupplementService service, MeterRegistry meterRegistry) {
        this.service = service;
        this.successCounter = Counter.builder("flowmesh.messaging.consumed")
            .tag("consumer", "supplier-supplement-requested").register(meterRegistry);
        this.failureCounter = Counter.builder("flowmesh.messaging.failed")
            .tag("consumer", "supplier-supplement-requested").register(meterRegistry);
        this.processingTimer = Timer.builder("flowmesh.messaging.processing")
            .description("RocketMQ 消费消息处理耗时")
            .tag("consumer", "supplier-supplement-requested")
            .publishPercentileHistogram()
            .register(meterRegistry);
    }

    /**
     * 处理一条补件请求事件。
     *
     * @param message RocketMQ 消息体
     */
    @Override
    public void onMessage(String message) {
        Timer.Sample sample = Timer.start();
        try {
            service.handleSupplementRequested(message);
            successCounter.increment();
        } catch (RuntimeException exception) {
            failureCounter.increment();
            throw exception;
        } finally {
            sample.stop(processingTimer);
        }
    }
}

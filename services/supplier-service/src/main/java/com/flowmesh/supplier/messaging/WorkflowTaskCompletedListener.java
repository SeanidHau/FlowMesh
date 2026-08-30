package com.flowmesh.supplier.messaging;

import com.flowmesh.supplier.application.WorkflowTaskCompletedService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

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

    private final WorkflowTaskCompletedService service;

    /**
     * 创建审批完成事件监听器。
     *
     * @param service 审批结果处理服务
     */
    public WorkflowTaskCompletedListener(WorkflowTaskCompletedService service) {
        this.service = service;
    }

    /**
     * 处理一条审批完成事件。
     *
     * @param message RocketMQ 消息体
     */
    @Override
    public void onMessage(String message) {
        service.apply(message);
    }
}

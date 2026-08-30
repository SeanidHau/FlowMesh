package com.flowmesh.workflow.messaging;

import com.flowmesh.workflow.application.WorkflowEventProjectionService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 消费 supplier-service 发布的供应商申请提交事件。
 */
@Component
@ConditionalOnProperty(name = "flowmesh.workflow.consumer.enabled", havingValue = "true")
@RocketMQMessageListener(
    topic = "supplier-events",
    selectorExpression = "ApplicationSubmitted",
    consumerGroup = "flowmesh-workflow"
)
public class ApplicationSubmittedListener implements RocketMQListener<String> {

    private final WorkflowEventProjectionService projectionService;

    /**
     * 创建申请提交事件监听器。
     *
     * @param projectionService 流程投影服务
     */
    public ApplicationSubmittedListener(WorkflowEventProjectionService projectionService) {
        this.projectionService = projectionService;
    }

    /**
     * 处理一条申请提交事件。
     *
     * @param message RocketMQ 消息体
     */
    @Override
    public void onMessage(String message) {
        projectionService.project(message);
    }
}

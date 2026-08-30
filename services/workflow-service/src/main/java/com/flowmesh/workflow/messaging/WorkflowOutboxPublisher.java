package com.flowmesh.workflow.messaging;

import com.flowmesh.workflow.domain.WorkflowOutboxEvent;
import com.flowmesh.workflow.repository.WorkflowOutboxEventRepository;
import java.time.Instant;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 将 workflow Outbox 事件发布到 RocketMQ。
 */
@Component
@ConditionalOnProperty(name = "flowmesh.workflow.outbox.enabled", havingValue = "true")
public class WorkflowOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(WorkflowOutboxPublisher.class);
    private static final long SEND_TIMEOUT_MILLIS = 3_000L;

    private final WorkflowOutboxEventRepository repository;
    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 创建 workflow Outbox 发布器。
     *
     * @param repository Outbox 仓储
     * @param rocketMQTemplate RocketMQ 消息模板
     */
    public WorkflowOutboxPublisher(
        WorkflowOutboxEventRepository repository,
        RocketMQTemplate rocketMQTemplate
    ) {
        this.repository = repository;
        this.rocketMQTemplate = rocketMQTemplate;
    }

    /**
     * 定时投递最早的一批待发布事件。
     */
    @Scheduled(fixedDelayString = "${flowmesh.workflow.outbox.publish-interval-ms:1000}")
    @Transactional
    public void publishPendingEvents() {
        for (WorkflowOutboxEvent event : repository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()) {
            try {
                Message<String> message = MessageBuilder.withPayload(event.getPayload())
                    .setHeader(MessageConst.PROPERTY_KEYS, event.getAggregateId().toString())
                    .build();
                rocketMQTemplate.syncSend(
                    event.getTopic() + ":" + event.getTag(),
                    message,
                    SEND_TIMEOUT_MILLIS
                );
                event.markPublished(Instant.now());
            } catch (RuntimeException exception) {
                event.recordFailure(exception.getMessage());
                log.warn(
                    "RocketMQ workflow 事件发布失败，eventId={}，attemptCount={}",
                    event.getId(),
                    event.getAttemptCount(),
                    exception
                );
            }
        }
    }
}

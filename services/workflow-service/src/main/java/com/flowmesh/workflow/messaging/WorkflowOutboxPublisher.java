package com.flowmesh.workflow.messaging;

import com.flowmesh.workflow.domain.WorkflowOutboxEvent;
import com.flowmesh.workflow.repository.WorkflowOutboxEventRepository;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

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
    private final WorkflowOutboxClaimService claimService;
    private final int maxAttempts;
    private final long retryBaseDelaySeconds;

    /**
     * 创建 workflow Outbox 发布器。
     *
     * @param repository Outbox 仓储
     * @param rocketMQTemplate RocketMQ 消息模板
     */
    public WorkflowOutboxPublisher(
        WorkflowOutboxEventRepository repository,
        RocketMQTemplate rocketMQTemplate,
        WorkflowOutboxClaimService claimService,
        @Value("${flowmesh.workflow.outbox.max-attempts:5}") int maxAttempts,
        @Value("${flowmesh.workflow.outbox.retry-base-delay-seconds:1}") long retryBaseDelaySeconds
    ) {
        this.repository = repository;
        this.rocketMQTemplate = rocketMQTemplate;
        this.claimService = claimService;
        this.maxAttempts = maxAttempts;
        this.retryBaseDelaySeconds = retryBaseDelaySeconds;
    }

    /**
     * 定时投递最早的一批待发布事件。
     */
    @Scheduled(fixedDelayString = "${flowmesh.workflow.outbox.publish-interval-ms:1000}")
    public void publishPendingEvents() {
        List<WorkflowOutboxEvent> events = claimService.claimBatch();
        for (WorkflowOutboxEvent event : events) {
            try {
                Message<String> message = MessageBuilder.withPayload(event.getPayload())
                    .setHeader(MessageConst.PROPERTY_KEYS, event.getAggregateId().toString())
                    .build();
                rocketMQTemplate.syncSend(
                    event.getTopic() + ":" + event.getTag(),
                    message,
                    SEND_TIMEOUT_MILLIS
                );
                repository.markPublishedIfClaimed(event.getId(), event.getClaimToken(), Instant.now());
            } catch (RuntimeException exception) {
                String error = exception.getMessage() == null ? "unknown" : exception.getMessage();
                int nextAttempt = event.getAttemptCount() + 1;
                if (nextAttempt >= maxAttempts) {
                    repository.markDeadLetteredIfClaimed(
                        event.getId(), event.getClaimToken(), error, Instant.now(), maxAttempts
                    );
                } else {
                    long delay = Math.min(900L, retryBaseDelaySeconds * (1L << Math.min(nextAttempt - 1, 9)));
                    repository.recordRetryIfClaimed(
                        event.getId(), event.getClaimToken(), error,
                        Instant.now().plus(Duration.ofSeconds(delay)), maxAttempts
                    );
                }
                log.warn(
                    "RocketMQ workflow 事件发布失败，eventId={}，attemptCount={}",
                    event.getId(),
                    nextAttempt,
                    exception
                );
            }
        }
    }
}

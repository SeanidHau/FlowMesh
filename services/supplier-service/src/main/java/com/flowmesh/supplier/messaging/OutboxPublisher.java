package com.flowmesh.supplier.messaging;

import com.flowmesh.supplier.domain.OutboxEvent;
import com.flowmesh.supplier.repository.OutboxEventRepository;
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
 * 将 supplier Outbox 事件发布到 RocketMQ。
 *
 * <p>发布成功才设置 {@code publishedAt}；发送异常仅记录重试信息，事件会在下一轮继续处理。
 * {@code flowmesh.outbox.enabled=false} 时不创建该组件，便于只运行数据库功能的本地测试。</p>
 */
@Component
@ConditionalOnProperty(name = "flowmesh.outbox.enabled", havingValue = "true")
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final long SEND_TIMEOUT_MILLIS = 3_000L;

    private final OutboxEventRepository outboxEventRepository;
    private final RocketMQTemplate rocketMQTemplate;
    private final OutboxClaimService outboxClaimService;
    private final int maxAttempts;
    private final long retryBaseDelaySeconds;

    /**
     * 创建 Outbox 发布器。
     *
     * @param outboxEventRepository Outbox 事件仓储
     * @param rocketMQTemplate RocketMQ 消息模板
     */
    public OutboxPublisher(
        OutboxEventRepository outboxEventRepository,
        RocketMQTemplate rocketMQTemplate,
        OutboxClaimService outboxClaimService,
        @Value("${flowmesh.outbox.max-attempts:5}") int maxAttempts,
        @Value("${flowmesh.outbox.retry-base-delay-seconds:1}") long retryBaseDelaySeconds
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.rocketMQTemplate = rocketMQTemplate;
        this.outboxClaimService = outboxClaimService;
        this.maxAttempts = maxAttempts;
        this.retryBaseDelaySeconds = retryBaseDelaySeconds;
    }

    /**
     * 定时投递最早的一批待发布事件。
     */
    @Scheduled(fixedDelayString = "${flowmesh.outbox.publish-interval-ms:1000}")
    public void publishPendingEvents() {
        List<OutboxEvent> events = outboxClaimService.claimBatch();
        for (OutboxEvent event : events) {
            publish(event);
        }
    }

    private void publish(OutboxEvent event) {
        try {
            Message<String> message = MessageBuilder.withPayload(event.getPayload())
                .setHeader(MessageConst.PROPERTY_KEYS, event.getAggregateId().toString())
                .build();
            rocketMQTemplate.syncSend(
                event.getTopic() + ":" + event.getTag(),
                message,
                SEND_TIMEOUT_MILLIS
            );
            outboxEventRepository.markPublishedIfClaimed(event.getId(), event.getClaimToken(), Instant.now());
        } catch (RuntimeException exception) {
            String error = exception.getMessage() == null ? "unknown" : exception.getMessage();
            int nextAttempt = event.getAttemptCount() + 1;
            if (nextAttempt >= maxAttempts) {
                outboxEventRepository.markDeadLetteredIfClaimed(
                    event.getId(), event.getClaimToken(), error, Instant.now(), maxAttempts
                );
            } else {
                long delay = Math.min(900L, retryBaseDelaySeconds * (1L << Math.min(nextAttempt - 1, 9)));
                outboxEventRepository.recordRetryIfClaimed(
                    event.getId(), event.getClaimToken(), error,
                    Instant.now().plus(Duration.ofSeconds(delay)), maxAttempts
                );
            }
            log.warn(
                "RocketMQ 事件发布失败，eventId={}，attemptCount={}",
                event.getId(),
                nextAttempt,
                exception
            );
        }
    }
}

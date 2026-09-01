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
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

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
    private final Counter publishedCounter;
    private final Counter failedCounter;
    private final Counter retryCounter;
    private final Counter deadLetterCounter;
    private final Counter confirmationFailedCounter;

    /**
     * 创建 Outbox 发布器。
     *
     * @param outboxEventRepository Outbox 事件仓储
     * @param rocketMQTemplate RocketMQ 消息模板
     * @param outboxClaimService Outbox 认领服务
     * @param meterRegistry Micrometer 指标注册器
     * @param maxAttempts 单条事件最大尝试次数
     * @param retryBaseDelaySeconds 指数退避的基础秒数
     */
    public OutboxPublisher(
        OutboxEventRepository outboxEventRepository,
        RocketMQTemplate rocketMQTemplate,
        OutboxClaimService outboxClaimService,
        MeterRegistry meterRegistry,
        @Value("${flowmesh.outbox.max-attempts:5}") int maxAttempts,
        @Value("${flowmesh.outbox.retry-base-delay-seconds:1}") long retryBaseDelaySeconds
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.rocketMQTemplate = rocketMQTemplate;
        this.outboxClaimService = outboxClaimService;
        this.maxAttempts = maxAttempts;
        this.retryBaseDelaySeconds = retryBaseDelaySeconds;
        this.publishedCounter = Counter.builder("flowmesh.outbox.published")
            .description("已成功发送到 RocketMQ 的 supplier Outbox 事件数")
            .register(meterRegistry);
        this.failedCounter = Counter.builder("flowmesh.outbox.failed")
            .description("supplier Outbox 发送失败次数")
            .register(meterRegistry);
        this.retryCounter = Counter.builder("flowmesh.outbox.retry")
            .description("supplier Outbox 重试次数")
            .register(meterRegistry);
        this.deadLetterCounter = Counter.builder("flowmesh.outbox.dead_lettered")
            .description("supplier Outbox 进入死信的事件数")
            .register(meterRegistry);
        this.confirmationFailedCounter = Counter.builder("flowmesh.outbox.confirmation_failed")
            .description("supplier Outbox 已发送但未完成数据库确认的事件数")
            .register(meterRegistry);
        io.micrometer.core.instrument.Gauge.builder(
                "flowmesh.outbox.pending", outboxEventRepository, OutboxEventRepository::countPending
            )
            .description("supplier Outbox 待发布事件数")
            .register(meterRegistry);
        io.micrometer.core.instrument.Gauge.builder(
                "flowmesh.outbox.dead_lettered.current", outboxEventRepository, OutboxEventRepository::countDeadLettered
            )
            .description("supplier Outbox 当前死信事件数")
            .register(meterRegistry);
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
            try {
                int updated = outboxEventRepository.markPublishedIfClaimed(
                    event.getId(), event.getClaimToken(), Instant.now()
                );
                if (updated == 1) {
                    publishedCounter.increment();
                } else {
                    confirmationFailedCounter.increment();
                    log.warn("RocketMQ 事件已发送但未完成 Outbox 确认，eventId={}", event.getId());
                }
            } catch (RuntimeException confirmationException) {
                confirmationFailedCounter.increment();
                log.error("RocketMQ 事件已发送但 Outbox 确认失败，eventId={}", event.getId(), confirmationException);
            }
        } catch (RuntimeException exception) {
            failedCounter.increment();
            String error = exception.getMessage() == null ? "unknown" : exception.getMessage();
            int nextAttempt = event.getAttemptCount() + 1;
            if (nextAttempt >= maxAttempts) {
                deadLetterCounter.increment();
                outboxEventRepository.markDeadLetteredIfClaimed(
                    event.getId(), event.getClaimToken(), error, Instant.now(), maxAttempts
                );
            } else {
                retryCounter.increment();
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

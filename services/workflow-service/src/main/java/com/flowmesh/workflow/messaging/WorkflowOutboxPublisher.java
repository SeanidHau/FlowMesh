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
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

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
    private final Counter publishedCounter;
    private final Counter failedCounter;
    private final Counter retryCounter;
    private final Counter deadLetterCounter;
    private final Counter confirmationFailedCounter;

    /**
     * 创建 workflow Outbox 发布器。
     *
     * @param repository Outbox 仓储
     * @param rocketMQTemplate RocketMQ 消息模板
     * @param claimService Outbox 认领服务
     * @param meterRegistry Micrometer 指标注册器
     * @param maxAttempts 单条事件最大尝试次数
     * @param retryBaseDelaySeconds 指数退避的基础秒数
     */
    public WorkflowOutboxPublisher(
        WorkflowOutboxEventRepository repository,
        RocketMQTemplate rocketMQTemplate,
        WorkflowOutboxClaimService claimService,
        MeterRegistry meterRegistry,
        @Value("${flowmesh.workflow.outbox.max-attempts:5}") int maxAttempts,
        @Value("${flowmesh.workflow.outbox.retry-base-delay-seconds:1}") long retryBaseDelaySeconds
    ) {
        this.repository = repository;
        this.rocketMQTemplate = rocketMQTemplate;
        this.claimService = claimService;
        this.maxAttempts = maxAttempts;
        this.retryBaseDelaySeconds = retryBaseDelaySeconds;
        this.publishedCounter = Counter.builder("flowmesh.outbox.published")
            .description("已成功发送到 RocketMQ 的 workflow Outbox 事件数")
            .register(meterRegistry);
        this.failedCounter = Counter.builder("flowmesh.outbox.failed")
            .description("workflow Outbox 发送失败次数")
            .register(meterRegistry);
        this.retryCounter = Counter.builder("flowmesh.outbox.retry")
            .description("workflow Outbox 重试次数")
            .register(meterRegistry);
        this.deadLetterCounter = Counter.builder("flowmesh.outbox.dead_lettered")
            .description("workflow Outbox 进入死信的事件数")
            .register(meterRegistry);
        this.confirmationFailedCounter = Counter.builder("flowmesh.outbox.confirmation_failed")
            .description("workflow Outbox 已发送但未完成数据库确认的事件数")
            .register(meterRegistry);
        io.micrometer.core.instrument.Gauge.builder(
                "flowmesh.outbox.pending", repository, WorkflowOutboxEventRepository::countPending
            )
            .description("workflow Outbox 待发布事件数")
            .register(meterRegistry);
        io.micrometer.core.instrument.Gauge.builder(
                "flowmesh.outbox.dead_lettered.current", repository, WorkflowOutboxEventRepository::countDeadLettered
            )
            .description("workflow Outbox 当前死信事件数")
            .register(meterRegistry);
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
                try {
                    int updated = repository.markPublishedIfClaimed(
                        event.getId(), event.getClaimToken(), Instant.now()
                    );
                    if (updated == 1) {
                        publishedCounter.increment();
                    } else {
                        confirmationFailedCounter.increment();
                        log.warn("RocketMQ workflow 事件已发送但未完成 Outbox 确认，eventId={}", event.getId());
                    }
                } catch (RuntimeException confirmationException) {
                    confirmationFailedCounter.increment();
                    log.error(
                        "RocketMQ workflow 事件已发送但 Outbox 确认失败，eventId={}",
                        event.getId(),
                        confirmationException
                    );
                }
            } catch (RuntimeException exception) {
                failedCounter.increment();
                String error = exception.getMessage() == null ? "unknown" : exception.getMessage();
                int nextAttempt = event.getAttemptCount() + 1;
                if (nextAttempt >= maxAttempts) {
                    deadLetterCounter.increment();
                    repository.markDeadLetteredIfClaimed(
                        event.getId(), event.getClaimToken(), error, Instant.now(), maxAttempts
                    );
                } else {
                    retryCounter.increment();
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

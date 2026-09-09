package com.flowmesh.risk.messaging;

import com.flowmesh.risk.domain.RiskOutboxEvent;
import com.flowmesh.risk.repository.RiskOutboxRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * 发布风控结果 Outbox，收到 RocketMQ ACK 后才确认事件完成。
 */
@Component
@ConditionalOnProperty(name = "flowmesh.risk.outbox-enabled", havingValue = "true")
public class RiskOutboxPublisher {

    private final RiskOutboxRepository repository;
    private final RocketMQTemplate rocketMQTemplate;
    private final int batchSize;
    private final long leaseSeconds;
    private final int maxAttempts;
    private final long retryBaseDelaySeconds;
    private final Counter publishedCounter;
    private final Counter failedCounter;
    private final Counter deadLetterCounter;

    /**
     * 创建风控 Outbox 发布器。
     *
     * @param repository Outbox 仓储
     * @param rocketMQTemplate RocketMQ 模板
     * @param meterRegistry 指标注册器
     * @param batchSize 批大小
     * @param leaseSeconds 认领租约秒数
     * @param maxAttempts 最大尝试次数
     * @param retryBaseDelaySeconds 退避基数
     */
    public RiskOutboxPublisher(
        RiskOutboxRepository repository,
        RocketMQTemplate rocketMQTemplate,
        MeterRegistry meterRegistry,
        @Value("${flowmesh.risk.batch-size:10}") int batchSize,
        @Value("${flowmesh.risk.lease-seconds:60}") long leaseSeconds,
        @Value("${flowmesh.risk.max-attempts:5}") int maxAttempts,
        @Value("${flowmesh.risk.retry-base-delay-seconds:1}") long retryBaseDelaySeconds
    ) {
        this.repository = repository;
        this.rocketMQTemplate = rocketMQTemplate;
        this.batchSize = batchSize;
        this.leaseSeconds = leaseSeconds;
        this.maxAttempts = maxAttempts;
        this.retryBaseDelaySeconds = retryBaseDelaySeconds;
        this.publishedCounter = Counter.builder("flowmesh.outbox.published").tag("service", "risk")
            .register(meterRegistry);
        this.failedCounter = Counter.builder("flowmesh.outbox.failed").tag("service", "risk")
            .register(meterRegistry);
        this.deadLetterCounter = Counter.builder("flowmesh.outbox.dead_lettered").tag("service", "risk")
            .register(meterRegistry);
    }

    /**
     * 定时认领并发布风控结果。
     */
    @Scheduled(fixedDelayString = "${flowmesh.risk.publish-interval-ms:1000}")
    public void publishBatch() {
        Instant now = Instant.now();
        UUID claimToken = UUID.randomUUID();
        List<RiskOutboxEvent> events = repository.claimBatch(
            batchSize, now, now.plusSeconds(leaseSeconds), claimToken
        );
        for (RiskOutboxEvent event : events) {
            try {
                rocketMQTemplate.syncSend(event.getTopic() + ":" + event.getTag(), event.getPayload());
                if (repository.markPublished(event.getId(), claimToken) == 1) {
                    publishedCounter.increment();
                }
            } catch (RuntimeException exception) {
                failedCounter.increment();
                int nextAttempt = event.getAttemptCount() + 1;
                boolean deadLettered = nextAttempt >= maxAttempts;
                Instant nextAttemptAt = deadLettered
                    ? now.plus(Duration.ofDays(3650))
                    : now.plusSeconds(retryBaseDelaySeconds * (1L << Math.min(nextAttempt - 1, 10)));
                repository.markFailed(
                    event.getId(), claimToken, nextAttempt, nextAttemptAt,
                    truncate(exception.getMessage()), deadLettered ? now : null
                );
                if (deadLettered) {
                    deadLetterCounter.increment();
                }
            }
        }
    }

    private String truncate(String message) {
        if (message == null) return "unknown";
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}

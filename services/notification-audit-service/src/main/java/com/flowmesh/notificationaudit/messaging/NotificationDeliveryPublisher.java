package com.flowmesh.notificationaudit.messaging;

import com.flowmesh.notificationaudit.config.NotificationDeliveryProperties;
import com.flowmesh.notificationaudit.domain.NotificationDelivery;
import com.flowmesh.notificationaudit.repository.NotificationDeliveryRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 将通知投递队列发送到外部 Webhook，并按指数退避处理失败。
 */
@Component
@ConditionalOnProperty(name = "flowmesh.notification.delivery.enabled", havingValue = "true")
public class NotificationDeliveryPublisher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryPublisher.class);

    private final NotificationDeliveryRepository repository;
    private final NotificationDeliveryClaimService claimService;
    private final NotificationWebhookClient webhookClient;
    private final NotificationDeliveryProperties properties;
    private final Counter deliveredCounter;
    private final Counter failedCounter;
    private final Counter retryCounter;
    private final Counter deadLetterCounter;

    /**
     * 创建通知投递发布器。
     *
     * @param repository 投递仓储
     * @param claimService 认领服务
     * @param webhookClient Webhook 客户端
     * @param properties 投递配置
     * @param meterRegistry 指标注册器
     */
    public NotificationDeliveryPublisher(
        NotificationDeliveryRepository repository,
        NotificationDeliveryClaimService claimService,
        NotificationWebhookClient webhookClient,
        NotificationDeliveryProperties properties,
        MeterRegistry meterRegistry
    ) {
        this.repository = repository;
        this.claimService = claimService;
        this.webhookClient = webhookClient;
        this.properties = properties;
        this.deliveredCounter = Counter.builder("flowmesh.notification.delivery.delivered")
            .description("外部通知成功投递次数").register(meterRegistry);
        this.failedCounter = Counter.builder("flowmesh.notification.delivery.failed")
            .description("外部通知投递失败次数").register(meterRegistry);
        this.retryCounter = Counter.builder("flowmesh.notification.delivery.retry")
            .description("外部通知重试次数").register(meterRegistry);
        this.deadLetterCounter = Counter.builder("flowmesh.notification.delivery.dead_lettered")
            .description("外部通知进入死信次数").register(meterRegistry);
        Gauge.builder("flowmesh.notification.delivery.pending", repository,
                NotificationDeliveryRepository::countPending)
            .description("外部通知待投递数量").register(meterRegistry);
        Gauge.builder("flowmesh.notification.delivery.dead_lettered.current", repository,
                NotificationDeliveryRepository::countDeadLettered)
            .description("外部通知当前死信数量").register(meterRegistry);
    }

    /**
     * 周期性批量投递通知。
     */
    @Scheduled(fixedDelayString = "${flowmesh.notification.delivery.publish-interval-ms:1000}")
    public void publishScheduled() {
        publishBatch();
    }

    /**
     * 执行一批投递，单条失败不会阻塞同批次的其他通知。
     */
    void publishBatch() {
        List<NotificationDelivery> deliveries = claimService.claimBatch();
        for (NotificationDelivery delivery : deliveries) {
            try {
                webhookClient.send(delivery);
                if (markDelivered(delivery)) {
                    deliveredCounter.increment();
                }
            } catch (RuntimeException exception) {
                recordFailure(delivery, exception);
            }
        }
    }

    @Transactional
    boolean markDelivered(NotificationDelivery delivery) {
        return repository.markDelivered(delivery.getId(), delivery.getClaimToken(), Instant.now()) == 1;
    }

    @Transactional
    void recordFailure(NotificationDelivery delivery, RuntimeException exception) {
        int attempts = delivery.getAttempts() + 1;
        boolean deadLettered = attempts >= properties.getMaxAttempts();
        String status = deadLettered ? "DEAD_LETTER" : "PENDING";
        Instant nextAttemptAt = Instant.now().plusSeconds(retryDelaySeconds(attempts));
        String error = exception.getClass().getSimpleName();
        int updated = repository.markFailed(
            delivery.getId(), delivery.getClaimToken(), attempts, nextAttemptAt, status, error
        );
        if (updated != 1) {
            log.warn("notification delivery lease lost: deliveryId={}", delivery.getId());
            return;
        }
        failedCounter.increment();
        if (deadLettered) {
            deadLetterCounter.increment();
            log.error("notification delivery moved to dead letter: deliveryId={}, attempts={}",
                delivery.getId(), attempts);
        } else {
            retryCounter.increment();
            log.warn("notification delivery scheduled for retry: deliveryId={}, attempts={}",
                delivery.getId(), attempts);
        }
    }

    private long retryDelaySeconds(int attempts) {
        long multiplier = 1L << Math.min(attempts - 1, 20);
        return Math.min(properties.getRetryBaseDelaySeconds() * multiplier, 3600L);
    }
}

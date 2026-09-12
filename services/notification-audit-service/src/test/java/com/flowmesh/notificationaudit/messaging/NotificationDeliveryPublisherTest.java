package com.flowmesh.notificationaudit.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowmesh.notificationaudit.config.NotificationDeliveryProperties;
import com.flowmesh.notificationaudit.domain.Notification;
import com.flowmesh.notificationaudit.domain.NotificationDelivery;
import com.flowmesh.notificationaudit.repository.NotificationDeliveryRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证外部通知投递的成功、重试和死信状态迁移。
 */
@ExtendWith(MockitoExtension.class)
class NotificationDeliveryPublisherTest {

    @Mock
    private NotificationDeliveryRepository repository;

    @Mock
    private NotificationDeliveryClaimService claimService;

    @Mock
    private NotificationWebhookClient webhookClient;

    private NotificationDeliveryProperties properties;
    private NotificationDeliveryPublisher publisher;
    private SimpleMeterRegistry meterRegistry;

    /**
     * 使用安全的测试参数创建发布器。
     */
    @BeforeEach
    void setUp() {
        properties = new NotificationDeliveryProperties();
        properties.setEnabled(true);
        properties.setMaxAttempts(3);
        properties.setRetryBaseDelaySeconds(1);
        meterRegistry = new SimpleMeterRegistry();
        publisher = new NotificationDeliveryPublisher(
            repository, claimService, webhookClient, properties, meterRegistry
        );
    }

    /**
     * 成功发送后必须使用认领令牌确认 delivered。
     */
    @Test
    void shouldMarkDeliveryAsDelivered() {
        NotificationDelivery delivery = delivery(0);
        when(claimService.claimBatch()).thenReturn(List.of(delivery));
        when(claimService.renew(delivery)).thenReturn(true);
        when(repository.markDelivered(eq(delivery.getId()), eq(delivery.getClaimToken()), any(Instant.class)))
            .thenReturn(1);

        publisher.publishBatch();

        verify(webhookClient).send(delivery);
        verify(repository).markDelivered(eq(delivery.getId()), eq(delivery.getClaimToken()), any(Instant.class));
    }

    /**
     * Webhook 已发送但队列确认失败时，必须记录确认失败指标，便于发现潜在重复投递。
     */
    @Test
    void shouldRecordConfirmationFailureWhenDeliveryCannotBeMarked() {
        NotificationDelivery delivery = delivery(0);
        when(claimService.claimBatch()).thenReturn(List.of(delivery));
        when(claimService.renew(delivery)).thenReturn(true);
        when(repository.markDelivered(eq(delivery.getId()), eq(delivery.getClaimToken()), any(Instant.class)))
            .thenReturn(0);

        publisher.publishBatch();

        verify(webhookClient).send(delivery);
        assertThat(meterRegistry.get("flowmesh.notification.delivery.confirmation_failed")
            .counter().count()).isEqualTo(1.0);
    }

    /**
     * 未达到最大尝试次数时应回到待发送状态并安排指数退避。
     */
    @Test
    void shouldScheduleRetryAfterFailure() {
        NotificationDelivery delivery = delivery(0);
        when(claimService.claimBatch()).thenReturn(List.of(delivery));
        when(claimService.renew(delivery)).thenReturn(true);
        doThrow(new IllegalStateException("remote unavailable")).when(webhookClient).send(delivery);
        when(repository.markFailed(
            eq(delivery.getId()), eq(delivery.getClaimToken()), eq(1), any(Instant.class), eq("PENDING"),
            eq("IllegalStateException")
        )).thenReturn(1);

        publisher.publishBatch();

        verify(repository).markFailed(
            eq(delivery.getId()), eq(delivery.getClaimToken()), eq(1), any(Instant.class), eq("PENDING"),
            eq("IllegalStateException")
        );
    }

    /**
     * 达到最大尝试次数后必须进入死信，避免无限重试拖垮外部系统。
     */
    @Test
    void shouldMoveDeliveryToDeadLetterAfterMaxAttempts() {
        NotificationDelivery delivery = delivery(2);
        when(claimService.claimBatch()).thenReturn(List.of(delivery));
        when(claimService.renew(delivery)).thenReturn(true);
        doThrow(new IllegalStateException("remote unavailable")).when(webhookClient).send(delivery);
        when(repository.markFailed(
            eq(delivery.getId()), eq(delivery.getClaimToken()), eq(3), any(Instant.class), eq("DEAD_LETTER"),
            eq("IllegalStateException")
        )).thenReturn(1);

        publisher.publishBatch();

        verify(repository).markFailed(
            eq(delivery.getId()), eq(delivery.getClaimToken()), eq(3), any(Instant.class), eq("DEAD_LETTER"),
            eq("IllegalStateException")
        );
    }

    /**
     * 租约无法续租时必须跳过 Webhook，避免旧实例在租约失效后继续产生外部副作用。
     */
    @Test
    void shouldSkipWebhookWhenLeaseCannotBeRenewed() {
        NotificationDelivery delivery = delivery(0);
        when(claimService.claimBatch()).thenReturn(List.of(delivery));
        when(claimService.renew(delivery)).thenReturn(false);

        publisher.publishBatch();

        verify(webhookClient, org.mockito.Mockito.never()).send(delivery);
        verify(repository, org.mockito.Mockito.never()).markDelivered(any(), any(), any());
        verify(repository, org.mockito.Mockito.never()).markFailed(any(), any(), any(Integer.class), any(), any(), any());
    }

    private NotificationDelivery delivery(int attempts) {
        NotificationDelivery delivery = NotificationDelivery.pending(Notification.unread(
            UUID.randomUUID(), "tenant-a", UUID.randomUUID(), "SUPPLIER_ACTIVATED", "title", "content"
        ));
        delivery.setAttempts(attempts);
        delivery.setClaimToken(UUID.randomUUID());
        return delivery;
    }
}

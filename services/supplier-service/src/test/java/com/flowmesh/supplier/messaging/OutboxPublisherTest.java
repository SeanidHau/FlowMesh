package com.flowmesh.supplier.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowmesh.supplier.domain.OutboxEvent;
import com.flowmesh.supplier.repository.OutboxEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;

/**
 * 验证 Outbox 发布器的成功确认、重试和死信分支。
 */
class OutboxPublisherTest {

    /**
     * 验证 RocketMQ ACK 返回后才确认 Outbox 已发布。
     */
    @Test
    void shouldMarkEventPublishedAfterRocketMqAck() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        OutboxClaimService claimService = mock(OutboxClaimService.class);
        RocketMQTemplate template = mock(RocketMQTemplate.class);
        OutboxEvent event = event();
        event.claim(UUID.randomUUID(), Instant.now().plusSeconds(30));
        when(claimService.claimBatch()).thenReturn(List.of(event));
        when(repository.markPublishedIfClaimed(any(), any(), any())).thenReturn(1);

        OutboxPublisher publisher = new OutboxPublisher(
            repository, template, claimService, new SimpleMeterRegistry(), 3, 1
        );
        publisher.publishPendingEvents();

        verify(template).syncSend(
            eq("supplier-events:ApplicationSubmitted"), any(Message.class), anyLong()
        );
        verify(repository).markPublishedIfClaimed(eq(event.getId()), eq(event.getClaimToken()), any());
    }

    /**
     * 验证数据库未确认时不计入发布成功，并记录确认失败指标。
     */
    @Test
    void shouldNotCountPublishWhenDatabaseConfirmationUpdatesNoRow() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        OutboxClaimService claimService = mock(OutboxClaimService.class);
        RocketMQTemplate template = mock(RocketMQTemplate.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        OutboxEvent event = event();
        event.claim(UUID.randomUUID(), Instant.now().plusSeconds(30));
        when(claimService.claimBatch()).thenReturn(List.of(event));
        when(repository.markPublishedIfClaimed(any(), any(), any())).thenReturn(0);

        OutboxPublisher publisher = new OutboxPublisher(
            repository, template, claimService, meterRegistry, 3, 1
        );
        publisher.publishPendingEvents();

        assertThat(meterRegistry.get("flowmesh.outbox.published").counter().count()).isZero();
        assertThat(meterRegistry.get("flowmesh.outbox.confirmation_failed").counter().count())
            .isEqualTo(1);
    }

    /**
     * 验证发送异常会按最大次数进入死信，而不是无边界重试。
     */
    @Test
    void shouldDeadLetterEventWhenMaximumAttemptsReached() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        OutboxClaimService claimService = mock(OutboxClaimService.class);
        RocketMQTemplate template = mock(RocketMQTemplate.class);
        OutboxEvent event = event();
        event.claim(UUID.randomUUID(), Instant.now().plusSeconds(30));
        when(claimService.claimBatch()).thenReturn(List.of(event));
        doThrow(new IllegalStateException("broker unavailable"))
            .when(template).syncSend(
                eq("supplier-events:ApplicationSubmitted"), any(Message.class), anyLong()
            );

        OutboxPublisher publisher = new OutboxPublisher(
            repository, template, claimService, new SimpleMeterRegistry(), 1, 1
        );
        publisher.publishPendingEvents();

        verify(repository).markDeadLetteredIfClaimed(
            eq(event.getId()), eq(event.getClaimToken()), eq("broker unavailable"), any(), eq(1)
        );
    }

    private OutboxEvent event() {
        return new OutboxEvent(
            UUID.randomUUID(), "tenant-a", UUID.randomUUID(), "supplier-events",
            "ApplicationSubmitted", "{\"eventId\":\"test\"}"
        );
    }
}

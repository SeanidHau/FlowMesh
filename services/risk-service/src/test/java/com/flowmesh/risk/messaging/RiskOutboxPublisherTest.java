package com.flowmesh.risk.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowmesh.risk.domain.RiskOutboxEvent;
import com.flowmesh.risk.repository.RiskOutboxRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;

/**
 * 验证风控 Outbox 发布前的租约续期和租约失效保护。
 */
class RiskOutboxPublisherTest {

    /**
     * 验证风控发布器拒绝超过边界的退避时间。
     */
    @Test
    void shouldRejectUnsafePublisherConfiguration() {
        assertThatThrownBy(() -> new RiskOutboxPublisher(
            mock(RiskOutboxRepository.class),
            mock(RocketMQTemplate.class),
            new SimpleMeterRegistry(),
            1,
            60,
            3,
            901,
            3000
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("retry-base-delay-seconds");
    }

    /**
     * 持有租约时才允许发送并在数据库确认成功后计入发布指标。
     */
    @Test
    void shouldRenewLeaseBeforePublishing() {
        RiskOutboxRepository repository = mock(RiskOutboxRepository.class);
        RocketMQTemplate template = mock(RocketMQTemplate.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        RiskOutboxEvent event = event();
        when(repository.claimBatch(any(Integer.class), any(), any(), any())).thenReturn(List.of(event));
        when(repository.renewClaim(eq(event.getId()), any(UUID.class), any())).thenReturn(1);
        when(repository.markPublished(eq(event.getId()), any(UUID.class))).thenReturn(1);

        new RiskOutboxPublisher(repository, template, meterRegistry, 1, 60, 3, 1, 3000)
            .publishBatch();

        verify(repository).renewClaim(eq(event.getId()), any(UUID.class), any());
        verify(template).syncSend(
            eq("risk-events:RiskCheckCompleted"), eq(event.getPayload()), anyLong()
        );
        assertThat(meterRegistry.get("flowmesh.outbox.published").tag("service", "risk")
            .counter().count()).isEqualTo(1);
    }

    /**
     * 租约已被其他发布器接管时，当前发布器不得再次发送事件。
     */
    @Test
    void shouldSkipPublishingWhenLeaseCannotBeRenewed() {
        RiskOutboxRepository repository = mock(RiskOutboxRepository.class);
        RocketMQTemplate template = mock(RocketMQTemplate.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        RiskOutboxEvent event = event();
        when(repository.claimBatch(any(Integer.class), any(), any(), any())).thenReturn(List.of(event));
        when(repository.renewClaim(eq(event.getId()), any(UUID.class), any())).thenReturn(0);

        new RiskOutboxPublisher(repository, template, meterRegistry, 1, 60, 3, 1, 3000)
            .publishBatch();

        org.mockito.Mockito.verifyNoInteractions(template);
        assertThat(meterRegistry.get("flowmesh.outbox.confirmation_failed").tag("service", "risk")
            .counter().count()).isEqualTo(1);
    }

    private RiskOutboxEvent event() {
        return new RiskOutboxEvent(
            UUID.randomUUID(), "tenant-a", UUID.randomUUID(), "risk-events",
            "RiskCheckCompleted", "{\"eventId\":\"test\"}"
        );
    }
}

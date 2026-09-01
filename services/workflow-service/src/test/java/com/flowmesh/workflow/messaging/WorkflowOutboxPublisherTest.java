package com.flowmesh.workflow.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowmesh.workflow.domain.WorkflowOutboxEvent;
import com.flowmesh.workflow.repository.WorkflowOutboxEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;

/**
 * 验证 workflow Outbox 发布器的 ACK 确认和指标语义。
 */
class WorkflowOutboxPublisherTest {

    /**
     * 验证只有数据库确认更新成功后才计入发布成功。
     */
    @Test
    void shouldCountPublishAfterDatabaseConfirmation() {
        WorkflowOutboxEventRepository repository = mock(WorkflowOutboxEventRepository.class);
        WorkflowOutboxClaimService claimService = mock(WorkflowOutboxClaimService.class);
        RocketMQTemplate template = mock(RocketMQTemplate.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        WorkflowOutboxEvent event = event();
        event.claim(UUID.randomUUID(), Instant.now().plusSeconds(60));
        when(claimService.claimBatch()).thenReturn(List.of(event));
        when(repository.markPublishedIfClaimed(any(), any(), any())).thenReturn(1);

        WorkflowOutboxPublisher publisher = new WorkflowOutboxPublisher(
            repository, template, claimService, meterRegistry, 3, 1
        );
        publisher.publishPendingEvents();

        verify(template).syncSend(
            eq("workflow-events:WorkflowTaskCompleted"), any(Message.class), anyLong()
        );
        assertThat(meterRegistry.get("flowmesh.outbox.published").counter().count()).isEqualTo(1);
        assertThat(meterRegistry.get("flowmesh.outbox.confirmation_failed").counter().count()).isZero();
    }

    /**
     * 验证数据库未确认时不计入发布成功，并记录确认失败指标。
     */
    @Test
    void shouldRecordConfirmationFailureWhenNoRowIsUpdated() {
        WorkflowOutboxEventRepository repository = mock(WorkflowOutboxEventRepository.class);
        WorkflowOutboxClaimService claimService = mock(WorkflowOutboxClaimService.class);
        RocketMQTemplate template = mock(RocketMQTemplate.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        WorkflowOutboxEvent event = event();
        event.claim(UUID.randomUUID(), Instant.now().plusSeconds(60));
        when(claimService.claimBatch()).thenReturn(List.of(event));
        when(repository.markPublishedIfClaimed(any(), any(), any())).thenReturn(0);

        WorkflowOutboxPublisher publisher = new WorkflowOutboxPublisher(
            repository, template, claimService, meterRegistry, 3, 1
        );
        publisher.publishPendingEvents();

        assertThat(meterRegistry.get("flowmesh.outbox.published").counter().count()).isZero();
        assertThat(meterRegistry.get("flowmesh.outbox.confirmation_failed").counter().count())
            .isEqualTo(1);
    }

    private WorkflowOutboxEvent event() {
        return new WorkflowOutboxEvent(
            UUID.randomUUID(), "tenant-a", UUID.randomUUID(), "workflow-events",
            "WorkflowTaskCompleted", "{\"eventId\":\"test\"}"
        );
    }
}

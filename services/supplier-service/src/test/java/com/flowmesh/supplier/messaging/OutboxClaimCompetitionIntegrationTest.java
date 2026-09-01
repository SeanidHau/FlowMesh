package com.flowmesh.supplier.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowmesh.supplier.domain.OutboxEvent;
import com.flowmesh.supplier.repository.OutboxEventRepository;
import com.flowmesh.supplier.rls.TenantRlsInitializer;
import com.flowmesh.supplier.support.PostgresIntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 验证多个发布器实例通过数据库租约竞争同一批事件时不会重复认领。
 */
class OutboxClaimCompetitionIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private OutboxEventRepository repository;

    @Autowired
    private OutboxClaimService claimService;

    @Autowired
    private TenantRlsInitializer tenantRlsInitializer;

    /**
     * 验证两个并发发布器最多只有一个能够认领同一事件。
     *
     * @throws Exception 并发任务执行失败时抛出
     */
    @Test
    void shouldClaimAnEventOnlyOnceAcrossConcurrentPublishers() throws Exception {
        tenantRlsInitializer.initializeTenant("tenant-a");
        UUID eventId = UUID.randomUUID();
        repository.save(new OutboxEvent(
            eventId, "tenant-a", UUID.randomUUID(), "supplier-events", "ApplicationSubmitted",
            "{\"eventId\":\"test\"}"
        ));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<List<OutboxEvent>> first = executor.submit(claimService::claimBatch);
            Future<List<OutboxEvent>> second = executor.submit(claimService::claimBatch);
            List<OutboxEvent> firstBatch = first.get();
            List<OutboxEvent> secondBatch = second.get();

            Set<UUID> claimedIds = new java.util.HashSet<>();
            firstBatch.forEach(event -> claimedIds.add(event.getId()));
            secondBatch.forEach(event -> claimedIds.add(event.getId()));
            assertThat(claimedIds).containsExactly(eventId);
            assertThat(firstBatch.size() + secondBatch.size()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }
}

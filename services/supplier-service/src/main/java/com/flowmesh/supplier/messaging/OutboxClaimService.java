package com.flowmesh.supplier.messaging;

import com.flowmesh.supplier.domain.OutboxEvent;
import com.flowmesh.supplier.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 在短数据库事务中认领待发布 Outbox 事件。
 */
@Service
public class OutboxClaimService {

    private static final int BATCH_SIZE = 100;
    private static final long LEASE_SECONDS = 30;

    private final OutboxEventRepository repository;

    /**
     * 创建 Outbox 认领服务。
     *
     * @param repository Outbox 仓储
     */
    public OutboxClaimService(OutboxEventRepository repository) {
        this.repository = repository;
    }

    /**
     * 锁定并标记一批待发布事件，网络发送在事务外执行。
     *
     * @return 已认领事件
     */
    @Transactional
    public List<OutboxEvent> claimBatch() {
        Instant now = Instant.now();
        UUID claimToken = UUID.randomUUID();
        List<OutboxEvent> events = repository.findAvailableForUpdate(now, BATCH_SIZE);
        events.forEach(event -> event.claim(claimToken, now.plusSeconds(LEASE_SECONDS)));
        repository.flush();
        return events;
    }
}

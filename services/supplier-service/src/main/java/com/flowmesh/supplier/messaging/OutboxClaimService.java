package com.flowmesh.supplier.messaging;

import com.flowmesh.supplier.domain.OutboxEvent;
import com.flowmesh.supplier.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 在短数据库事务中认领待发布 Outbox 事件。
 */
@Service
public class OutboxClaimService {

    private final OutboxEventRepository repository;
    private final int batchSize;
    private final long leaseSeconds;

    /**
     * 创建 Outbox 认领服务。
     *
     * @param repository Outbox 仓储
     * @param batchSize 单次认领的最大事件数
     * @param leaseSeconds 单次认领租约秒数
     * @param sendTimeoutMillis 单条消息发送超时时间
     */
    public OutboxClaimService(
        OutboxEventRepository repository,
        @Value("${flowmesh.outbox.batch-size:10}") int batchSize,
        @Value("${flowmesh.outbox.lease-seconds:60}") long leaseSeconds,
        @Value("${flowmesh.outbox.send-timeout-ms:3000}") long sendTimeoutMillis
    ) {
        validateConfiguration(batchSize, leaseSeconds, sendTimeoutMillis);
        this.repository = repository;
        this.batchSize = batchSize;
        this.leaseSeconds = leaseSeconds;
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
        return repository.claimBatch(now, claimToken, now.plusSeconds(leaseSeconds), batchSize);
    }

    /**
     * 在网络发送前续租单条事件，避免批次中前序耗时导致当前事件失去所有权。
     *
     * @param event 待发送事件
     * @return 仍持有租约时为 {@code true}
     */
    @Transactional
    public boolean renew(OutboxEvent event) {
        return repository.renewClaim(
            event.getId(), event.getClaimToken(), Instant.now().plusSeconds(leaseSeconds)
        ) == 1;
    }

    private void validateConfiguration(int configuredBatchSize, long configuredLeaseSeconds,
                                       long configuredSendTimeoutMillis) {
        if (configuredBatchSize < 1 || configuredBatchSize > 100) {
            throw new IllegalArgumentException("flowmesh.outbox.batch-size must be between 1 and 100");
        }
        if (configuredSendTimeoutMillis < 100) {
            throw new IllegalArgumentException("flowmesh.outbox.send-timeout-ms must be at least 100");
        }
        long minimumLeaseSeconds = (configuredBatchSize * configuredSendTimeoutMillis + 999) / 1000 + 10;
        if (configuredLeaseSeconds < minimumLeaseSeconds) {
            throw new IllegalArgumentException(
                "flowmesh.outbox.lease-seconds must cover the batch send timeout plus a safety margin"
            );
        }
    }
}

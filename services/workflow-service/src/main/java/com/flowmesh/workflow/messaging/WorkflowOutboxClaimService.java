package com.flowmesh.workflow.messaging;

import com.flowmesh.workflow.domain.WorkflowOutboxEvent;
import com.flowmesh.workflow.repository.WorkflowOutboxEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 在短数据库事务中认领 workflow 待发布 Outbox 事件。
 */
@Service
public class WorkflowOutboxClaimService {

    private final WorkflowOutboxEventRepository repository;
    private final int batchSize;
    private final long leaseSeconds;

    /**
     * 创建 workflow Outbox 认领服务。
     *
     * @param repository Outbox 仓储
     * @param batchSize 单次认领的最大事件数
     * @param leaseSeconds 单次认领租约秒数
     */
    public WorkflowOutboxClaimService(
        WorkflowOutboxEventRepository repository,
        @Value("${flowmesh.workflow.outbox.batch-size:10}") int batchSize,
        @Value("${flowmesh.workflow.outbox.lease-seconds:60}") long leaseSeconds
    ) {
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
    public List<WorkflowOutboxEvent> claimBatch() {
        Instant now = Instant.now();
        UUID claimToken = UUID.randomUUID();
        return repository.claimBatch(now, claimToken, now.plusSeconds(leaseSeconds), batchSize);
    }
}

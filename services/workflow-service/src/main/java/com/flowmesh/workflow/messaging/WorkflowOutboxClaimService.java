package com.flowmesh.workflow.messaging;

import com.flowmesh.workflow.domain.WorkflowOutboxEvent;
import com.flowmesh.workflow.repository.WorkflowOutboxEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 在短数据库事务中认领 workflow 待发布 Outbox 事件。
 */
@Service
public class WorkflowOutboxClaimService {

    private static final int BATCH_SIZE = 100;
    private static final long LEASE_SECONDS = 30;

    private final WorkflowOutboxEventRepository repository;

    /**
     * 创建 workflow Outbox 认领服务。
     *
     * @param repository Outbox 仓储
     */
    public WorkflowOutboxClaimService(WorkflowOutboxEventRepository repository) {
        this.repository = repository;
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
        return repository.claimBatch(now, claimToken, now.plusSeconds(LEASE_SECONDS), BATCH_SIZE);
    }
}

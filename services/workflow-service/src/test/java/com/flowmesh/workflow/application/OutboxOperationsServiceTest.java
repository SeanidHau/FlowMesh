package com.flowmesh.workflow.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmesh.common.security.AuthPrincipal;
import com.flowmesh.workflow.repository.OutboxReplayAuditRepository;
import com.flowmesh.workflow.repository.WorkflowOutboxEventRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 验证 workflow 死信查询的返回规模边界。
 */
class OutboxOperationsServiceTest {

    private final WorkflowOutboxEventRepository outboxRepository = mock(WorkflowOutboxEventRepository.class);
    private final OutboxOperationsService service = new OutboxOperationsService(
        outboxRepository,
        mock(OutboxReplayAuditRepository.class),
        mock(ObjectMapper.class)
    );
    private final AuthPrincipal principal = new AuthPrincipal(
        UUID.randomUUID(), "operator", "tenant-a", Set.of("OPERATIONS")
    );

    /**
     * 小于最小值的请求必须被收敛为一条，避免数据库收到无效 LIMIT。
     */
    @Test
    void shouldNormalizeLimitToMinimum() {
        when(outboxRepository.findDeadLettered("tenant-a", null, null, 1)).thenReturn(List.of());

        service.listDeadLetters(principal, null, null, 0);

        verify(outboxRepository).findDeadLettered("tenant-a", null, null, 1);
    }

    /**
     * 大于最大值的请求必须被收敛为一百条，避免运维查询占用过多资源。
     */
    @Test
    void shouldNormalizeLimitToMaximum() {
        when(outboxRepository.findDeadLettered("tenant-a", null, null, 100)).thenReturn(List.of());

        service.listDeadLetters(principal, null, null, 1000);

        verify(outboxRepository).findDeadLettered("tenant-a", null, null, 100);
    }
}

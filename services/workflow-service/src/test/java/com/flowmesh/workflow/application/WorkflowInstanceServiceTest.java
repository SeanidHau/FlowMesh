package com.flowmesh.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.flowmesh.common.security.AuthPrincipal;
import com.flowmesh.workflow.domain.WorkflowInstance;
import com.flowmesh.workflow.domain.WorkflowTask;
import com.flowmesh.workflow.repository.WorkflowInstanceRepository;
import com.flowmesh.workflow.repository.WorkflowOutboxEventRepository;
import com.flowmesh.workflow.rls.TenantRlsInitializer;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证审批节点按角色和顺序推进。
 */
@ExtendWith(MockitoExtension.class)
class WorkflowInstanceServiceTest {

    @Mock
    private WorkflowInstanceRepository repository;

    @Mock
    private WorkflowOutboxEventRepository outboxRepository;

    @Mock
    private TenantRlsInitializer tenantRlsInitializer;

    /**
     * 验证采购审批完成后进入法务审批节点。
     */
    @Test
    void shouldAdvanceTaskForRequiredRole() {
        UUID applicationId = UUID.randomUUID();
        WorkflowInstance instance = new WorkflowInstance(
            applicationId, UUID.randomUUID(), "tenant-a"
        );
        when(repository.findByApplicationId(applicationId)).thenReturn(Optional.of(instance));
        when(repository.save(instance)).thenReturn(instance);

        WorkflowInstanceService service = new WorkflowInstanceService(
            repository,
            outboxRepository,
            tenantRlsInitializer,
            new ObjectMapper().registerModule(new JavaTimeModule())
        );
        AuthPrincipal principal = new AuthPrincipal(
            UUID.randomUUID(), "purchaser-a", "tenant-a", Set.of("PURCHASER")
        );

        WorkflowInstance result = service.completeTask(
            principal, applicationId, WorkflowTask.PURCHASER_REVIEW.name(), "trace-1"
        );

        assertThat(result.getCurrentTask()).isEqualTo(WorkflowTask.LEGAL_REVIEW);
    }
}

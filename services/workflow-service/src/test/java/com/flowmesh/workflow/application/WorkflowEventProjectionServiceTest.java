package com.flowmesh.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmesh.workflow.domain.WorkflowInstance;
import com.flowmesh.workflow.repository.WorkflowInstanceRepository;
import com.flowmesh.workflow.rls.TenantRlsInitializer;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * 验证 workflow 消费投影的最小幂等行为。
 */
@ExtendWith(MockitoExtension.class)
class WorkflowEventProjectionServiceTest {

    @Mock
    private WorkflowInstanceRepository repository;

    @Mock
    private TenantRlsInitializer tenantRlsInitializer;

    /**
     * 验证同一事件第二次到达时不会创建第二个流程实例。
     */
    @Test
    void shouldIgnoreDuplicateEvent() {
        UUID eventId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        String message = """
            {
              "eventId":"%s",
              "eventType":"ApplicationSubmitted",
              "schemaVersion":1,
              "aggregateId":"%s",
              "tenantId":"tenant-a",
              "occurredAt":"2026-08-31T00:00:00Z",
              "traceId":"trace-test",
              "payload":{
                "applicationId":"%s",
                "supplierName":"测试供应商",
                "applicantUserId":"00000000-0000-0000-0000-000000000001"
              }
            }
            """.formatted(eventId, applicationId, applicationId);

        when(repository.existsBySourceEventId(eventId)).thenReturn(false, true);
        WorkflowEventProjectionService service = new WorkflowEventProjectionService(
            repository, new ObjectMapper(), tenantRlsInitializer, new SimpleMeterRegistry()
        );

        service.project(message);
        service.project(message);

        ArgumentCaptor<WorkflowInstance> captor = ArgumentCaptor.forClass(WorkflowInstance.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getApplicationId()).isEqualTo(applicationId);
        assertThat(captor.getValue().getTenantId()).isEqualTo("tenant-a");
    }
}

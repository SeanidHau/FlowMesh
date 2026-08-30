package com.flowmesh.supplier.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowmesh.supplier.domain.ApplicationStatus;
import com.flowmesh.supplier.domain.SupplierApplication;
import com.flowmesh.supplier.repository.OutboxEventRepository;
import com.flowmesh.supplier.repository.SupplierApplicationRepository;
import com.flowmesh.supplier.repository.WorkflowEventInboxRepository;
import com.flowmesh.supplier.rls.TenantRlsInitializer;
import com.flowmesh.supplier.support.PostgresIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * 验证 workflow 审批结果回写 supplier 的状态和事件闭环。
 */
@Transactional
class WorkflowTaskCompletedServiceIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private SupplierApplicationRepository applicationRepository;

    @Autowired
    private WorkflowEventInboxRepository inboxRepository;

    @Autowired
    private OutboxEventRepository outboxRepository;

    @Autowired
    private TenantRlsInitializer tenantRlsInitializer;

    @Autowired
    private WorkflowTaskCompletedService service;

    /**
     * 验证审批结果更新申请、重复事件不重复推进，运营节点生成启用事件。
     */
    @Test
    void shouldApplyWorkflowResultIdempotentlyAndPublishActivation() {
        tenantRlsInitializer.initializeTenant("tenant-a");
        SupplierApplication application = applicationRepository.saveAndFlush(
            new SupplierApplication("tenant-a", UUID.randomUUID(), "闭环供应商")
        );
        UUID applicationId = application.getId();
        UUID firstEventId = UUID.randomUUID();

        String firstEvent = event(firstEventId, applicationId, "PURCHASER_REVIEW");
        service.apply(firstEvent);
        service.apply(firstEvent);

        SupplierApplication afterReview = applicationRepository.findById(applicationId).orElseThrow();
        assertThat(afterReview.getStatus()).isEqualTo(ApplicationStatus.IN_REVIEW);
        assertThat(afterReview.getStateVersion()).isEqualTo(1);
        assertThat(inboxRepository.count()).isEqualTo(1);

        service.apply(event(UUID.randomUUID(), applicationId, "OPERATIONS_ACTIVATION"));

        SupplierApplication enabled = applicationRepository.findById(applicationId).orElseThrow();
        assertThat(enabled.getStatus()).isEqualTo(ApplicationStatus.ENABLED);
        assertThat(enabled.getStateVersion()).isEqualTo(2);
        assertThat(outboxRepository
            .findByAggregateIdAndTag(applicationId, "SupplierActivated"))
            .isPresent();
    }

    private String event(UUID eventId, UUID applicationId, String taskKey) {
        return """
            {
              "eventId":"%s",
              "eventType":"WorkflowTaskCompleted",
              "tenantId":"tenant-a",
              "aggregateId":"%s",
              "traceId":"trace-test",
              "payload":{"taskKey":"%s"}
            }
            """.formatted(eventId, applicationId, taskKey);
    }
}

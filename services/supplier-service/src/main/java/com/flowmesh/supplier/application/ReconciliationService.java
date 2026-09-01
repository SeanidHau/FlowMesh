package com.flowmesh.supplier.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmesh.common.security.AuthPrincipal;
import com.flowmesh.supplier.api.dto.ReconciliationResponse;
import com.flowmesh.supplier.domain.ApplicationStatus;
import com.flowmesh.supplier.domain.OutboxEvent;
import com.flowmesh.supplier.domain.ReconciliationCase;
import com.flowmesh.supplier.domain.SupplierApplication;
import com.flowmesh.supplier.repository.OutboxEventRepository;
import com.flowmesh.supplier.repository.ReconciliationCaseRepository;
import com.flowmesh.supplier.repository.SupplierApplicationRepository;
import com.flowmesh.supplier.repository.WorkflowEventInboxRepository;
import com.flowmesh.supplier.rls.TenantRlsInitializer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 对比 supplier 本地状态、Inbox/Outbox 和 workflow 快照，并生成待处置记录。
 */
@Service
public class ReconciliationService {

    private final SupplierApplicationRepository applicationRepository;
    private final OutboxEventRepository outboxRepository;
    private final WorkflowEventInboxRepository inboxRepository;
    private final ReconciliationCaseRepository caseRepository;
    private final WorkflowStateClient workflowStateClient;
    private final TenantRlsInitializer tenantRlsInitializer;
    private final ObjectMapper objectMapper;

    /**
     * 创建对账服务。
     *
     * @param applicationRepository 申请仓储
     * @param outboxRepository Outbox 仓储
     * @param inboxRepository Inbox 仓储
     * @param caseRepository 对账记录仓储
     * @param workflowStateClient workflow 状态客户端
     * @param tenantRlsInitializer RLS 初始化器
     * @param objectMapper JSON 处理器
     */
    public ReconciliationService(
        SupplierApplicationRepository applicationRepository,
        OutboxEventRepository outboxRepository,
        WorkflowEventInboxRepository inboxRepository,
        ReconciliationCaseRepository caseRepository,
        WorkflowStateClient workflowStateClient,
        TenantRlsInitializer tenantRlsInitializer,
        ObjectMapper objectMapper
    ) {
        this.applicationRepository = applicationRepository;
        this.outboxRepository = outboxRepository;
        this.inboxRepository = inboxRepository;
        this.caseRepository = caseRepository;
        this.workflowStateClient = workflowStateClient;
        this.tenantRlsInitializer = tenantRlsInitializer;
        this.objectMapper = objectMapper;
    }

    /**
     * 对账当前租户的指定申请。
     *
     * @param principal 当前操作者
     * @param applicationId 申请标识
     * @param bearerToken 当前访问令牌
     * @return 对账结果
     */
    @Transactional
    public ReconciliationResponse reconcile(
        AuthPrincipal principal,
        UUID applicationId,
        String bearerToken
    ) {
        tenantRlsInitializer.initializeTenant(principal.tenantId());
        SupplierApplication application = applicationRepository.findById(applicationId)
            .orElseThrow(SupplierApplicationNotFoundException::new);
        OutboxEvent submission = outboxRepository
            .findByTenantIdAndAggregateIdAndTag(
                principal.tenantId(), applicationId, "ApplicationSubmitted"
            ).orElse(null);
        List<String> discrepancies = new ArrayList<>();
        if (submission != null && submission.getDeadLetteredAt() != null) {
            discrepancies.add("SUBMISSION_EVENT_DEAD_LETTERED");
        }

        WorkflowStateClient.WorkflowState workflow;
        try {
            workflow = workflowStateClient.find(principal.tenantId(), applicationId, bearerToken).orElse(null);
        } catch (RuntimeException exception) {
            workflow = null;
            discrepancies.add("WORKFLOW_UNAVAILABLE");
        }
        final WorkflowStateClient.WorkflowState workflowState = workflow;

        if (workflow == null && !discrepancies.contains("WORKFLOW_UNAVAILABLE")
            && submission != null && submission.getPublishedAt() != null) {
            discrepancies.add("WORKFLOW_INSTANCE_MISSING");
        }
        if (workflow != null) {
            if ("COMPLETED".equals(workflow.status()) && application.getStatus() != ApplicationStatus.ENABLED) {
                discrepancies.add("APPLICATION_STATUS_MISMATCH");
            }
            if (application.getStatus() == ApplicationStatus.ENABLED && !"COMPLETED".equals(workflow.status())) {
                discrepancies.add("WORKFLOW_STATUS_MISMATCH");
            }
            if ("COMPLETED".equals(workflow.status()) && inboxRepository.countByAggregateId(applicationId) == 0) {
                discrepancies.add("WORKFLOW_COMPLETION_NOT_CONSUMED");
            }
            if (workflow.outboxPendingCount() > 0) {
                discrepancies.add("WORKFLOW_OUTBOX_PENDING");
            }
        }
        if (application.getStatus() == ApplicationStatus.ENABLED
            && outboxRepository.countByTenantIdAndAggregateIdAndTag(
                principal.tenantId(), applicationId, "SupplierActivated"
            ) == 0) {
            discrepancies.add("ACTIVATION_EVENT_MISSING");
        }

        if (discrepancies.isEmpty()) {
            caseRepository.resolveOpenByApplication(principal.tenantId(), applicationId);
            return new ReconciliationResponse(applicationId, true, List.of(), List.of());
        }
        List<UUID> caseIds = discrepancies.stream().map(type -> {
            ReconciliationCase reconciliationCase = new ReconciliationCase(
                principal.tenantId(), applicationId, type, details(application, submission, workflowState, type)
            );
            caseRepository.upsertOpen(reconciliationCase);
            return reconciliationCase.getId();
        }).toList();
        return new ReconciliationResponse(applicationId, false, List.copyOf(discrepancies), caseIds);
    }

    private String details(
        SupplierApplication application,
        OutboxEvent submission,
        WorkflowStateClient.WorkflowState workflow,
        String discrepancyType
    ) {
        try {
            return objectMapper.writeValueAsString(java.util.Map.of(
                "type", discrepancyType,
                "applicationStatus", application.getStatus().name(),
                "submissionPublished", submission != null && submission.getPublishedAt() != null,
                "workflowStatus", workflow == null ? "MISSING" : workflow.status(),
                "workflowOutboxPending", workflow == null ? 0 : workflow.outboxPendingCount()
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("对账详情序列化失败", exception);
        }
    }
}

package com.flowmesh.supplier.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmesh.common.messaging.EventEnvelopeValidator;
import com.flowmesh.supplier.domain.SupplierApplication;
import com.flowmesh.supplier.domain.WorkflowEventInbox;
import com.flowmesh.supplier.repository.SupplierApplicationRepository;
import com.flowmesh.supplier.repository.WorkflowEventInboxRepository;
import com.flowmesh.supplier.rls.TenantRlsInitializer;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 处理 workflow 发来的风控拒绝结果，保持申请状态与流程终态一致。
 */
@Service
public class WorkflowRiskRejectedService {

    private final SupplierApplicationRepository applicationRepository;
    private final WorkflowEventInboxRepository inboxRepository;
    private final TenantRlsInitializer tenantRlsInitializer;
    private final ObjectMapper objectMapper;

    /**
     * 创建风控拒绝结果服务。
     *
     * @param applicationRepository 申请仓储
     * @param inboxRepository workflow 事件 Inbox
     * @param tenantRlsInitializer RLS 初始化器
     * @param objectMapper JSON 解析器
     */
    public WorkflowRiskRejectedService(
        SupplierApplicationRepository applicationRepository,
        WorkflowEventInboxRepository inboxRepository,
        TenantRlsInitializer tenantRlsInitializer,
        ObjectMapper objectMapper
    ) {
        this.applicationRepository = applicationRepository;
        this.inboxRepository = inboxRepository;
        this.tenantRlsInitializer = tenantRlsInitializer;
        this.objectMapper = objectMapper;
    }

    /**
     * 应用风控拒绝事件，重复事件不会重复推进申请状态。
     *
     * @param message RocketMQ 事件信封
     */
    @Transactional
    public void apply(String message) {
        JsonNode event = readEvent(message);
        UUID eventId = EventEnvelopeValidator.requiredUuid(event, "eventId");
        UUID applicationId = EventEnvelopeValidator.requiredUuid(event, "aggregateId");
        String tenantId = EventEnvelopeValidator.requiredText(event, "tenantId");
        JsonNode payload = EventEnvelopeValidator.validate(event, "WorkflowRiskRejected");
        UUID applicantUserId = EventEnvelopeValidator.requiredUuid(payload, "applicantUserId");
        EventEnvelopeValidator.requiredText(payload, "reason");
        tenantRlsInitializer.initializeTenant(tenantId);
        if (inboxRepository.existsById(eventId)) {
            return;
        }
        SupplierApplication application = applicationRepository.findById(applicationId)
            .orElseThrow(SupplierApplicationNotFoundException::new);
        if (!application.getApplicantUserId().equals(applicantUserId)) {
            throw new IllegalArgumentException("风控拒绝事件的申请人与申请记录不一致");
        }
        application.rejectRisk();
        if (applicationRepository.updateState(application) != 1) {
            throw new OptimisticLockingFailureException("风控拒绝更新时申请版本发生冲突");
        }
        inboxRepository.save(new WorkflowEventInbox(eventId, tenantId, applicationId));
    }

    private JsonNode readEvent(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            EventEnvelopeValidator.validate(event, "WorkflowRiskRejected");
            return event;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("风控拒绝事件 JSON 无效", exception);
        }
    }
}

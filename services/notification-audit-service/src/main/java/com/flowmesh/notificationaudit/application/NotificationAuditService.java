package com.flowmesh.notificationaudit.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmesh.common.messaging.EventEnvelopeValidator;
import com.flowmesh.notificationaudit.domain.Notification;
import com.flowmesh.notificationaudit.repository.AuditEventRepository;
import com.flowmesh.notificationaudit.repository.NotificationRepository;
import com.flowmesh.notificationaudit.rls.TenantRlsInitializer;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 将供应商启用事件投影为不可变审计记录和站内通知。
 */
@Service
public class NotificationAuditService {

    private final AuditEventRepository auditEventRepository;
    private final NotificationRepository notificationRepository;
    private final TenantRlsInitializer tenantRlsInitializer;
    private final ObjectMapper objectMapper;

    /**
     * 创建通知审计应用服务。
     *
     * @param auditEventRepository 审计事件仓储
     * @param notificationRepository 通知仓储
     * @param tenantRlsInitializer RLS 初始化器
     * @param objectMapper JSON 解析器
     */
    public NotificationAuditService(
        AuditEventRepository auditEventRepository,
        NotificationRepository notificationRepository,
        TenantRlsInitializer tenantRlsInitializer,
        ObjectMapper objectMapper
    ) {
        this.auditEventRepository = auditEventRepository;
        this.notificationRepository = notificationRepository;
        this.tenantRlsInitializer = tenantRlsInitializer;
        this.objectMapper = objectMapper;
    }

    /**
     * 处理供应商启用事件，重复事件只保留一份投影。
     *
     * @param message RocketMQ 事件信封
     */
    @Transactional
    public void handleSupplierActivated(String message) {
        JsonNode event = readEvent(message);
        UUID eventId = EventEnvelopeValidator.requiredUuid(event, "eventId");
        UUID aggregateId = EventEnvelopeValidator.requiredUuid(event, "aggregateId");
        String tenantId = EventEnvelopeValidator.requiredText(event, "tenantId");
        Instant occurredAt = Instant.parse(EventEnvelopeValidator.requiredText(event, "occurredAt"));
        String traceId = EventEnvelopeValidator.requiredText(event, "traceId");
        JsonNode payload = EventEnvelopeValidator.validate(event, "SupplierActivated");
        UUID applicantUserId = EventEnvelopeValidator.requiredUuid(payload, "applicantUserId");
        String supplierName = EventEnvelopeValidator.requiredText(payload, "supplierName");

        tenantRlsInitializer.initialize(tenantId);
        if (auditEventRepository.existsByEventId(eventId)) {
            return;
        }
        auditEventRepository.insert(
            UUID.randomUUID(), eventId, tenantId, aggregateId, "SupplierActivated", traceId,
            message, occurredAt
        );
        notificationRepository.insert(Notification.unread(
            eventId,
            tenantId,
            applicantUserId,
            "SUPPLIER_ACTIVATED",
            "供应商已启用",
            "供应商「" + supplierName + "」已完成准入审批并启用。"
        ));
        auditEventRepository.insertInbox(eventId, tenantId, aggregateId);
    }

    /**
     * 处理审批 SLA 催办或超时升级事件。
     *
     * @param message Workflow SLA 事件信封
     */
    @Transactional
    public void handleWorkflowTaskSla(String message) {
        JsonNode event = readSlaEvent(message);
        UUID eventId = EventEnvelopeValidator.requiredUuid(event, "eventId");
        UUID aggregateId = EventEnvelopeValidator.requiredUuid(event, "aggregateId");
        String tenantId = EventEnvelopeValidator.requiredText(event, "tenantId");
        Instant occurredAt = Instant.parse(EventEnvelopeValidator.requiredText(event, "occurredAt"));
        String traceId = EventEnvelopeValidator.requiredText(event, "traceId");
        String eventType = EventEnvelopeValidator.requiredText(event, "eventType");
        if (!Set.of("WorkflowTaskSlaReminderRequested", "WorkflowTaskSlaEscalated").contains(eventType)) {
            throw new IllegalArgumentException("不支持的 Workflow SLA 事件类型");
        }
        JsonNode payload = EventEnvelopeValidator.validate(event, eventType);
        UUID applicantUserId = EventEnvelopeValidator.requiredUuid(payload, "applicantUserId");
        String taskKey = EventEnvelopeValidator.requiredText(payload, "taskKey");

        tenantRlsInitializer.initialize(tenantId);
        if (auditEventRepository.existsByEventId(eventId)) {
            return;
        }
        boolean escalated = "WorkflowTaskSlaEscalated".equals(eventType);
        String title = escalated ? "审批任务已升级" : "审批任务即将超时";
        String content = escalated
            ? "供应商申请的 " + taskKey + " 已超过 SLA，系统已转运营处置。"
            : "供应商申请的 " + taskKey + " 即将达到 SLA，请尽快处理。";
        auditEventRepository.insert(
            UUID.randomUUID(), eventId, tenantId, aggregateId, eventType, traceId,
            message, occurredAt
        );
        notificationRepository.insert(Notification.unread(
            eventId, tenantId, applicantUserId,
            escalated ? "WORKFLOW_TASK_ESCALATED" : "WORKFLOW_TASK_REMINDER",
            title,
            content
        ));
        auditEventRepository.insertInbox(eventId, tenantId, aggregateId);
    }

    /**
     * 查询用户的站内通知。
     *
     * @param tenantId 租户标识
     * @param userId 用户标识
     * @param limit 返回上限
     * @return 通知列表
     */
    @Transactional(readOnly = true)
    public List<Notification> findNotifications(String tenantId, UUID userId, int limit) {
        tenantRlsInitializer.initialize(tenantId);
        return notificationRepository.findForUser(tenantId, userId, Math.min(Math.max(limit, 1), 100));
    }

    /**
     * 将当前用户可访问的通知标记为已读，并保持操作幂等。
     *
     * @param tenantId 租户标识
     * @param userId 当前用户标识
     * @param notificationId 通知标识
     * @throws NotificationNotFoundException 通知不存在或不属于当前用户
     */
    @Transactional
    public void markNotificationAsRead(String tenantId, UUID userId, UUID notificationId) {
        tenantRlsInitializer.initialize(tenantId);
        if (notificationRepository.markAsRead(tenantId, userId, notificationId, Instant.now()) != 1) {
            throw new NotificationNotFoundException(notificationId);
        }
    }

    private JsonNode readSlaEvent(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            EventEnvelopeValidator.validate(event, "SupplierActivated");
            return event;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("供应商启用事件 JSON 无效", exception);
        }
    }

    private JsonNode readEvent(String message) {
        try {
            return objectMapper.readTree(message);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Workflow SLA 事件 JSON 无效", exception);
        }
    }
}

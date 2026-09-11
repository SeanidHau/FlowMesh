package com.flowmesh.notificationaudit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.flowmesh.notificationaudit.domain.Notification;
import com.flowmesh.notificationaudit.repository.AuditEventRepository;
import com.flowmesh.notificationaudit.repository.NotificationRepository;
import com.flowmesh.notificationaudit.rls.TenantRlsInitializer;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证启用事件同时生成审计与站内通知，且重复事件不会重复写入副作用。
 */
@ExtendWith(MockitoExtension.class)
class NotificationAuditServiceTest {

    @Mock
    private AuditEventRepository auditRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationDeliveryEnqueuer notificationDeliveryEnqueuer;

    @Mock
    private TenantRlsInitializer tenantRlsInitializer;

    /**
     * 首次消费应同时写入审计记录、通知和 Inbox。
     */
    @Test
    void shouldProjectSupplierActivation() {
        UUID eventId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID applicantUserId = UUID.randomUUID();
        when(auditRepository.existsByEventId(eventId)).thenReturn(false);

        newService().handleSupplierActivated(message(eventId, applicationId, applicantUserId));

        verify(auditRepository).insert(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq(eventId),
            org.mockito.ArgumentMatchers.eq("tenant-a"),
            org.mockito.ArgumentMatchers.eq(applicationId),
            org.mockito.ArgumentMatchers.eq("SupplierActivated"),
            org.mockito.ArgumentMatchers.eq("trace-test"),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any()
        );
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).insert(captor.capture());
        assertThat(captor.getValue().getRecipientUserId()).isEqualTo(applicantUserId);
        verify(notificationDeliveryEnqueuer).enqueue(captor.getValue());
        verify(auditRepository).insertInbox(eventId, "tenant-a", applicationId);
    }

    /**
     * SLA 催办事件应生成申请人通知和不可变审计记录。
     */
    @Test
    void shouldProjectWorkflowSlaReminder() {
        UUID eventId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID applicantUserId = UUID.randomUUID();
        when(auditRepository.existsByEventId(eventId)).thenReturn(false);

        newService().handleWorkflowTaskSla(
            slaMessage(eventId, applicationId, applicantUserId, "WorkflowTaskSlaReminderRequested")
        );

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).insert(captor.capture());
        assertThat(captor.getValue().getRecipientUserId()).isEqualTo(applicantUserId);
        assertThat(captor.getValue().getNotificationType()).isEqualTo("WORKFLOW_TASK_REMINDER");
        verify(notificationDeliveryEnqueuer).enqueue(captor.getValue());
        verify(auditRepository).insertInbox(eventId, "tenant-a", applicationId);
    }

    /**
     * SLA 超时升级事件应生成运营升级通知，而不是被错误事件类型校验拒绝。
     */
    @Test
    void shouldProjectWorkflowSlaEscalation() {
        UUID eventId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID applicantUserId = UUID.randomUUID();
        when(auditRepository.existsByEventId(eventId)).thenReturn(false);

        newService().handleWorkflowTaskSla(
            slaMessage(eventId, applicationId, applicantUserId, "WorkflowTaskSlaEscalated")
        );

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).insert(captor.capture());
        assertThat(captor.getValue().getRecipientUserId()).isEqualTo(applicantUserId);
        assertThat(captor.getValue().getNotificationType()).isEqualTo("WORKFLOW_TASK_ESCALATED");
        verify(notificationDeliveryEnqueuer).enqueue(captor.getValue());
        verify(auditRepository).insertInbox(eventId, "tenant-a", applicationId);
    }

    /**
     * 重复事件只允许命中 Inbox 查询，不重复生成通知。
     */
    @Test
    void shouldIgnoreDuplicateEvent() {
        UUID eventId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID applicantUserId = UUID.randomUUID();
        when(auditRepository.existsByEventId(eventId)).thenReturn(true);

        newService().handleSupplierActivated(message(eventId, applicationId, applicantUserId));

        org.mockito.Mockito.verifyNoInteractions(notificationRepository);
        org.mockito.Mockito.verifyNoInteractions(notificationDeliveryEnqueuer);
    }

    /**
     * 当前用户可以重复调用已读操作，服务只按租户和用户范围更新通知。
     */
    @Test
    void shouldMarkOwnNotificationAsRead() {
        UUID notificationId = UUID.randomUUID();
        when(notificationRepository.markAsRead(
            org.mockito.ArgumentMatchers.eq("tenant-a"),
            org.mockito.ArgumentMatchers.eq(UUID.fromString("00000000-0000-0000-0000-000000000001")),
            org.mockito.ArgumentMatchers.eq(notificationId),
            org.mockito.ArgumentMatchers.any(Instant.class)
        )).thenReturn(1);

        newService().markNotificationAsRead(
            "tenant-a",
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            notificationId
        );

        verify(notificationRepository).markAsRead(
            org.mockito.ArgumentMatchers.eq("tenant-a"),
            org.mockito.ArgumentMatchers.any(UUID.class),
            org.mockito.ArgumentMatchers.eq(notificationId),
            org.mockito.ArgumentMatchers.any(Instant.class)
        );
    }

    /**
     * 跨租户或跨用户的通知必须表现为不存在，避免泄露资源归属信息。
     */
    @Test
    void shouldRejectNotificationOutsideCurrentUser() {
        UUID notificationId = UUID.randomUUID();
        when(notificationRepository.markAsRead(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(UUID.class),
            org.mockito.ArgumentMatchers.eq(notificationId),
            org.mockito.ArgumentMatchers.any(Instant.class)
        )).thenReturn(0);

        assertThatThrownBy(() -> newService().markNotificationAsRead(
            "tenant-a",
            UUID.randomUUID(),
            notificationId
        )).isInstanceOf(NotificationNotFoundException.class);
    }

    private NotificationAuditService newService() {
        return new NotificationAuditService(
            auditRepository,
            notificationRepository,
            notificationDeliveryEnqueuer,
            tenantRlsInitializer,
            new ObjectMapper().registerModule(new JavaTimeModule())
        );
    }

    private String message(UUID eventId, UUID applicationId, UUID applicantUserId) {
        return """
            {
              "eventId":"%s",
              "eventType":"SupplierActivated",
              "schemaVersion":1,
              "tenantId":"tenant-a",
              "aggregateId":"%s",
              "occurredAt":"2026-09-09T00:00:00Z",
              "traceId":"trace-test",
              "payload":{
                "applicantUserId":"%s",
                "supplierName":"测试供应商"
              }
            }
        """.formatted(eventId, applicationId, applicantUserId);
    }

    private String slaMessage(
        UUID eventId,
        UUID applicationId,
        UUID applicantUserId,
        String eventType
    ) {
        return """
            {
              "eventId":"%s",
              "eventType":"%s",
              "schemaVersion":1,
              "tenantId":"tenant-a",
              "aggregateId":"%s",
              "occurredAt":"2026-09-11T00:00:00Z",
              "traceId":"trace-sla",
              "payload":{
                "applicantUserId":"%s",
                "taskKey":"LEGAL_REVIEW"
              }
            }
            """.formatted(eventId, eventType, applicationId, applicantUserId);
    }
}

package com.flowmesh.notificationaudit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.flowmesh.notificationaudit.domain.Notification;
import com.flowmesh.notificationaudit.repository.AuditEventRepository;
import com.flowmesh.notificationaudit.repository.NotificationRepository;
import com.flowmesh.notificationaudit.rls.TenantRlsInitializer;
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
    }

    private NotificationAuditService newService() {
        return new NotificationAuditService(
            auditRepository,
            notificationRepository,
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
}

package com.flowmesh.risk.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.flowmesh.risk.domain.RiskOutboxEvent;
import com.flowmesh.risk.domain.RiskResult;
import com.flowmesh.risk.repository.RiskOutboxRepository;
import com.flowmesh.risk.repository.RiskResultRepository;
import com.flowmesh.risk.rls.TenantRlsInitializer;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证风控决策和结果 Outbox 的事务边界输入。
 */
@ExtendWith(MockitoExtension.class)
class RiskEvaluationServiceTest {

    @Mock
    private RiskResultRepository resultRepository;

    @Mock
    private RiskOutboxRepository outboxRepository;

    @Mock
    private TenantRlsInitializer tenantRlsInitializer;

    /**
     * 供应商名称命中可复现规则时应拒绝，并写入拒绝结果事件。
     */
    @Test
    void shouldRejectSupplierMatchingDemoRule() {
        UUID applicationId = UUID.randomUUID();
        when(resultRepository.findByApplicationId(applicationId)).thenReturn(Optional.empty());
        RiskEvaluationService service = newService();

        service.evaluate(message(applicationId, "拒绝供应商 reject-demo"));

        ArgumentCaptor<RiskResult> resultCaptor = ArgumentCaptor.forClass(RiskResult.class);
        verify(resultRepository).insert(resultCaptor.capture());
        assertThat(resultCaptor.getValue().getDecision()).isEqualTo(RiskResult.Decision.REJECT);

        ArgumentCaptor<RiskOutboxEvent> eventCaptor = ArgumentCaptor.forClass(RiskOutboxEvent.class);
        verify(outboxRepository).insert(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getTag()).isEqualTo("RiskCheckCompleted");
        assertThat(eventCaptor.getValue().getPayload()).contains("\"decision\":\"REJECT\"");
    }

    /**
     * 普通供应商应通过风控，并写入通过结果事件。
     */
    @Test
    void shouldPassNormalSupplier() {
        UUID applicationId = UUID.randomUUID();
        when(resultRepository.findByApplicationId(applicationId)).thenReturn(Optional.empty());
        RiskEvaluationService service = newService();

        service.evaluate(message(applicationId, "正常供应商"));

        ArgumentCaptor<RiskResult> captor = ArgumentCaptor.forClass(RiskResult.class);
        verify(resultRepository).insert(captor.capture());
        assertThat(captor.getValue().getDecision()).isEqualTo(RiskResult.Decision.PASS);
    }

    private RiskEvaluationService newService() {
        return new RiskEvaluationService(
            resultRepository,
            outboxRepository,
            tenantRlsInitializer,
            new ObjectMapper().registerModule(new JavaTimeModule())
        );
    }

    private String message(UUID applicationId, String supplierName) {
        return """
            {
              "eventId":"%s",
              "eventType":"RiskCheckRequested",
              "schemaVersion":1,
              "tenantId":"tenant-a",
              "aggregateId":"%s",
              "occurredAt":"2026-09-09T00:00:00Z",
              "traceId":"trace-test",
              "payload":{
                "applicationId":"%s",
                "supplierName":"%s"
              }
            }
            """.formatted(UUID.randomUUID(), applicationId, applicationId, supplierName);
    }
}

package com.flowmesh.common.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * 验证跨服务事件信封的版本和关键字段校验。
 */
class EventEnvelopeValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldAcceptSupportedEnvelope() throws Exception {
        var event = objectMapper.readTree("""
            {
              "eventId":"00000000-0000-0000-0000-000000000001",
              "eventType":"ApplicationSubmitted",
              "schemaVersion":1,
              "tenantId":"tenant-a",
              "aggregateId":"00000000-0000-0000-0000-000000000002",
              "occurredAt":"2026-08-31T00:00:00Z",
              "traceId":"trace-test",
              "payload":{}
            }
            """);

        assertThat(EventEnvelopeValidator.validate(event, "ApplicationSubmitted")).isEmpty();
    }

    @Test
    void shouldRejectUnsupportedVersion() throws Exception {
        var event = objectMapper.readTree("""
            {
              "eventId":"00000000-0000-0000-0000-000000000001",
              "eventType":"ApplicationSubmitted",
              "schemaVersion":2,
              "tenantId":"tenant-a",
              "aggregateId":"00000000-0000-0000-0000-000000000002",
              "occurredAt":"2026-08-31T00:00:00Z",
              "traceId":"trace-test",
              "payload":{}
            }
            """);

        assertThatThrownBy(() -> EventEnvelopeValidator.validate(event, "ApplicationSubmitted"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("不支持的事件结构版本");
    }
}

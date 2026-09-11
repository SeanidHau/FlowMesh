package com.flowmesh.workflow.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmesh.workflow.application.WorkflowEventProjectionService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * 验证 RocketMQ 消费线程恢复事件中的 Trace ID。
 */
class ApplicationSubmittedListenerTest {

    /** 清理当前测试线程的日志上下文，避免测试之间互相污染。 */
    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    /**
     * 验证流程投影处理期间可从 MDC 读取事件 Trace ID，处理结束后不泄漏到线程池。
     */
    @Test
    void shouldRestoreTraceIdDuringMessageHandling() {
        WorkflowEventProjectionService service = mock(WorkflowEventProjectionService.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AtomicReference<String> traceId = new AtomicReference<>();
        doAnswer(invocation -> {
            traceId.set(MDC.get("traceId"));
            return null;
        }).when(service).project(anyString());

        ApplicationSubmittedListener listener = new ApplicationSubmittedListener(
            service, new ObjectMapper(), meterRegistry
        );
        listener.onMessage("{\"traceId\":\"trace-workflow-1\"}");

        assertThat(traceId).hasValue("trace-workflow-1");
        assertThat(MDC.get("traceId")).isNull();
        assertThat(meterRegistry.get("flowmesh.messaging.processing")
            .tag("consumer", "workflow-application-submitted").timer().count()).isEqualTo(1);
    }
}

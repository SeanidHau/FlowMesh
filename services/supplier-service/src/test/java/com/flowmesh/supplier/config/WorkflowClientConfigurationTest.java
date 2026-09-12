package com.flowmesh.supplier.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.web.client.RestClient;

/**
 * 验证对账客户端的下游 HTTP 传播契约。
 */
class WorkflowClientConfigurationTest {

    private HttpServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldPropagateTraceIdToWorkflowService() {
        String expectedTraceId = "trace-reconciliation-test";
        String applicationId = UUID.randomUUID().toString();
        String[] actualTraceId = new String[1];
        server.createContext("/internal/v1/reconciliation/workflow-instances/" + applicationId,
            exchange -> {
                actualTraceId[0] = exchange.getRequestHeaders().getFirst("X-Trace-Id");
                writeResponse(exchange, 200, "{\"status\":\"COMPLETED\","
                    + "\"currentTask\":null,\"outboxPendingCount\":0,\"outboxEventCount\":1}");
            });
        server.start();

        var client = new WorkflowClientConfiguration().workflowStateClient(
            RestClient.builder(),
            "http://localhost:" + server.getAddress().getPort(),
            Duration.ofSeconds(1),
            Duration.ofSeconds(1)
        );

        try (MDC.MDCCloseable ignored = MDC.putCloseable("traceId", expectedTraceId)) {
            var result = client.find("tenant-a", UUID.fromString(applicationId), "Bearer token");
            assertThat(result).isPresent();
        }

        assertThat(actualTraceId[0]).isEqualTo(expectedTraceId);
    }

    @Test
    void shouldNotSendTraceHeaderWhenNoTraceContextExists() {
        String applicationId = UUID.randomUUID().toString();
        String[] actualTraceId = new String[1];
        server.createContext("/internal/v1/reconciliation/workflow-instances/" + applicationId,
            exchange -> {
                actualTraceId[0] = exchange.getRequestHeaders().getFirst("X-Trace-Id");
                writeResponse(exchange, 200, "{\"status\":\"PENDING\","
                    + "\"currentTask\":\"PURCHASER_REVIEW\","
                    + "\"outboxPendingCount\":0,\"outboxEventCount\":1}");
            });
        server.start();

        var client = new WorkflowClientConfiguration().workflowStateClient(
            RestClient.builder(),
            "http://localhost:" + server.getAddress().getPort(),
            Duration.ofSeconds(1),
            Duration.ofSeconds(1)
        );

        var result = client.find("tenant-a", UUID.fromString(applicationId), "Bearer token");

        assertThat(result).isPresent();
        assertThat(actualTraceId[0]).isNull();
    }

    private static void writeResponse(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}

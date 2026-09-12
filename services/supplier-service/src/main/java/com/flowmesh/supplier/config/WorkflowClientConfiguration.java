package com.flowmesh.supplier.config;

import com.flowmesh.supplier.application.WorkflowStateClient;
import com.flowmesh.common.security.TraceIdFilter;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import java.time.Duration;
import org.slf4j.MDC;

/**
 * 配置 supplier 到 workflow 的内部对账读取客户端。
 */
@Configuration
public class WorkflowClientConfiguration {

    /**
     * 创建 workflow 状态客户端。
     *
     * @param builder Spring Web 客户端构建器
     * @param baseUrl workflow 服务地址
     * @param connectTimeout 连接超时时间
     * @param readTimeout 读取超时时间
     * @return workflow 状态客户端
     */
    @Bean
    public WorkflowStateClient workflowStateClient(
        RestClient.Builder builder,
        @Value("${flowmesh.workflow.base-url:http://localhost:8083}") String baseUrl,
        @Value("${flowmesh.workflow.client.connect-timeout:2s}") Duration connectTimeout,
        @Value("${flowmesh.workflow.client.read-timeout:3s}") Duration readTimeout
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        RestClient client = builder
            .requestFactory(requestFactory)
            .baseUrl(baseUrl)
            .build();
        return (tenantId, applicationId, bearerToken) -> {
            try {
                var request = client.get()
                    .uri("/internal/v1/reconciliation/workflow-instances/{applicationId}", applicationId)
                    .header("Authorization", bearerToken)
                    .header("X-Tenant-Id", tenantId);
                String traceId = MDC.get("traceId");
                if (traceId != null && !traceId.isBlank()) {
                    request.header(TraceIdFilter.TRACE_ID_HEADER, traceId);
                }
                WorkflowSnapshot response = request.retrieve()
                    .body(WorkflowSnapshot.class);
                return Optional.ofNullable(response).map(snapshot -> new WorkflowStateClient.WorkflowState(
                    snapshot.status(), snapshot.currentTask(), snapshot.outboxPendingCount(), snapshot.outboxEventCount()
                ));
            } catch (RestClientException exception) {
                if (exception instanceof org.springframework.web.client.HttpClientErrorException.NotFound) {
                    return Optional.empty();
                }
                throw exception;
            }
        };
    }

    private record WorkflowSnapshot(
        String status,
        String currentTask,
        long outboxPendingCount,
        long outboxEventCount
    ) {
    }
}

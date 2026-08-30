package com.flowmesh.workflow.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmesh.workflow.domain.WorkflowInstance;
import com.flowmesh.workflow.repository.WorkflowInstanceRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 将供应商申请提交事件投影为流程实例。
 */
@Service
public class WorkflowEventProjectionService {

    private final WorkflowInstanceRepository workflowInstanceRepository;
    private final ObjectMapper objectMapper;

    /**
     * 创建流程事件投影服务。
     *
     * @param workflowInstanceRepository 流程实例仓储
     * @param objectMapper JSON 解析器
     */
    public WorkflowEventProjectionService(
        WorkflowInstanceRepository workflowInstanceRepository,
        ObjectMapper objectMapper
    ) {
        this.workflowInstanceRepository = workflowInstanceRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 消费一条申请提交事件，重复事件直接忽略。
     *
     * @param message RocketMQ 消息体
     */
    @Transactional
    public void project(String message) {
        JsonNode event = readEvent(message);
        UUID eventId = UUID.fromString(event.required("eventId").asText());
        if (workflowInstanceRepository.existsBySourceEventId(eventId)) {
            return;
        }

        UUID applicationId = UUID.fromString(event.required("aggregateId").asText());
        String tenantId = event.required("tenantId").asText();
        workflowInstanceRepository.save(new WorkflowInstance(applicationId, eventId, tenantId));
    }

    private JsonNode readEvent(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            if (!"ApplicationSubmitted".equals(event.required("eventType").asText())) {
                throw new IllegalArgumentException("不支持的事件类型");
            }
            return event;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("事件 JSON 无效", exception);
        }
    }
}

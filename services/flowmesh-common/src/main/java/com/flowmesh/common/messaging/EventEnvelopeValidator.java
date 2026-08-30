package com.flowmesh.common.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/**
 * 校验跨服务 JSON 事件信封的公共字段。
 *
 * <p>消费者必须在进入业务事务前拒绝未知版本、缺失字段和非法标识，避免坏消息
 * 被当作合法业务事件处理。</p>
 */
public final class EventEnvelopeValidator {

    private static final int SUPPORTED_SCHEMA_VERSION = 1;

    private EventEnvelopeValidator() {
    }

    /**
     * 校验统一事件信封并返回 payload 节点。
     *
     * @param event 待校验 JSON
     * @param expectedType 消费者期望的事件类型
     * @return 非空对象 payload
     * @throws IllegalArgumentException 事件不符合当前契约
     */
    public static JsonNode validate(JsonNode event, String expectedType) {
        if (event == null || !event.isObject()) {
            throw new IllegalArgumentException("事件信封必须是 JSON 对象");
        }
        if (!expectedType.equals(requiredText(event, "eventType"))) {
            throw new IllegalArgumentException("不支持的事件类型");
        }
        if (event.path("schemaVersion").asInt(-1) != SUPPORTED_SCHEMA_VERSION) {
            throw new IllegalArgumentException("不支持的事件结构版本");
        }
        requiredUuid(event, "eventId");
        requiredUuid(event, "aggregateId");
        if (requiredText(event, "tenantId").length() > 64) {
            throw new IllegalArgumentException("事件租户标识过长");
        }
        try {
            Instant.parse(requiredText(event, "occurredAt"));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("事件发生时间无效", exception);
        }
        requiredText(event, "traceId");
        JsonNode payload = event.path("payload");
        if (!payload.isObject()) {
            throw new IllegalArgumentException("事件 payload 必须是 JSON 对象");
        }
        return payload;
    }

    /**
     * 读取事件中的 UUID 字段。
     *
     * @param event 事件对象
     * @param field 字段名
     * @return UUID
     */
    public static UUID requiredUuid(JsonNode event, String field) {
        try {
            return UUID.fromString(requiredText(event, field));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("事件字段 " + field + " 不是有效 UUID", exception);
        }
    }

    /**
     * 读取非空文本字段。
     *
     * @param event 事件对象
     * @param field 字段名
     * @return 字段文本
     */
    public static String requiredText(JsonNode event, String field) {
        JsonNode value = event.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("事件缺少字段 " + field);
        }
        return value.asText();
    }
}

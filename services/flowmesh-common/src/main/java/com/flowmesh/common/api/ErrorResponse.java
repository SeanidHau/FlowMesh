package com.flowmesh.common.api;

import java.util.List;

/**
 * 统一错误响应模型。
 *
 * <p>所有服务的 {@code @RestControllerAdvice} 在映射 HTTP 错误状态码时，
 * 统一返回该结构，使客户端可以按固定字段解析错误信息。</p>
 *
 * @param code 错误代码，如 {@code INVALID_CREDENTIALS}、{@code IDEMPOTENCY_KEY_CONFLICT}
 * @param message 人类可读的错误描述
 * @param traceId 请求追踪标识；由 HTTP Trace ID 上下文生成或透传
 * @param details 补充细节，如字段级校验错误列表
 */
public record ErrorResponse(
    String code,
    String message,
    String traceId,
    List<String> details
) {
    /**
     * 创建一个无补充细节的错误响应。
     *
     * @param code 错误代码
     * @param message 错误描述
     * @param traceId 请求追踪标识
     * @return 错误响应
     */
    public static ErrorResponse of(String code, String message, String traceId) {
        return new ErrorResponse(code, message, traceId, List.of());
    }
}

package com.flowmesh.notificationaudit.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowmesh.common.api.ErrorResponse;
import com.flowmesh.common.security.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * 验证 notification-audit 服务的统一 HTTP 异常响应契约。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private HttpServletRequest request;

    /** 初始化带有最终 Trace ID 的模拟请求。 */
    @BeforeEach
    void setUp() {
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.setAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE, "trace-test");
        request = mockRequest;
    }

    /** 验证非法请求格式返回可解析的 400 错误结构。 */
    @Test
    void shouldMapMalformedRequestToStableErrorResponse() {
        ResponseEntity<ErrorResponse> response = handler.handleRequestBinding(
            new HttpMessageNotReadableException("malformed json", new IllegalArgumentException()), request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(ErrorResponse.of(
            "INVALID_REQUEST", "请求格式或参数类型不正确。", "trace-test"
        ));
    }

    /** 验证未预期异常不会把内部错误详情返回给客户端。 */
    @Test
    void shouldHideUnexpectedExceptionDetails() {
        ResponseEntity<ErrorResponse> response = handler.handleUnexpected(
            new IllegalStateException("database password must not leak"), request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isEqualTo(ErrorResponse.of(
            "INTERNAL_ERROR", "服务内部错误。", "trace-test"
        ));
    }
}

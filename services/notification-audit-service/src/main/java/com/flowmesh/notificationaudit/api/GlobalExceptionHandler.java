package com.flowmesh.notificationaudit.api;

import com.flowmesh.common.api.ErrorResponse;
import com.flowmesh.common.security.TraceIdFilter;
import com.flowmesh.notificationaudit.application.NotificationNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * notification-audit 服务 API 异常映射。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 映射通知不存在或不属于当前用户的情况。
     *
     * @param exception 业务异常
     * @param request 当前请求
     * @return 404 错误响应
     */
    @ExceptionHandler(NotificationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotificationNotFound(
        NotificationNotFoundException exception,
        HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(
            "NOTIFICATION_NOT_FOUND",
            "通知不存在或不属于当前用户。",
            TraceIdFilter.currentTraceId(request),
            java.util.List.of()
        ));
    }
}

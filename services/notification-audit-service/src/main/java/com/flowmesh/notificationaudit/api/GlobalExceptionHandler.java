package com.flowmesh.notificationaudit.api;

import com.flowmesh.common.api.ErrorResponse;
import com.flowmesh.common.security.TraceIdFilter;
import com.flowmesh.notificationaudit.application.NotificationNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * notification-audit 服务 API 异常映射。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理请求字段校验错误。
     *
     * @param exception 参数校验异常
     * @param request 当前请求
     * @return 400 错误响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
        MethodArgumentNotValidException exception,
        HttpServletRequest request
    ) {
        List<String> details = exception.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .toList();
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "请求参数校验失败。", request, details);
    }

    /**
     * 处理请求体格式、查询参数类型或必填参数错误。
     *
     * @param exception 请求绑定异常
     * @param request 当前请求
     * @return 400 错误响应
     */
    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class
    })
    public ResponseEntity<ErrorResponse> handleRequestBinding(
        Exception exception,
        HttpServletRequest request
    ) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "请求格式或参数类型不正确。", request);
    }

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
        return response(
            HttpStatus.NOT_FOUND,
            "NOTIFICATION_NOT_FOUND",
            "通知不存在或不属于当前用户。",
            request
        );
    }

    /**
     * 处理未预期异常，避免向客户端暴露堆栈、SQL 或依赖错误详情。
     *
     * @param exception 未预期异常
     * @param request 当前请求
     * @return 500 错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
        Exception exception,
        HttpServletRequest request
    ) {
        log.error("未预期异常", exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "服务内部错误。", request);
    }

    private static ResponseEntity<ErrorResponse> response(
        HttpStatus status,
        String code,
        String message,
        HttpServletRequest request
    ) {
        return response(status, code, message, request, List.of());
    }

    private static ResponseEntity<ErrorResponse> response(
        HttpStatus status,
        String code,
        String message,
        HttpServletRequest request,
        List<String> details
    ) {
        return ResponseEntity.status(status).body(new ErrorResponse(
            code, message, TraceIdFilter.currentTraceId(request), details
        ));
    }
}

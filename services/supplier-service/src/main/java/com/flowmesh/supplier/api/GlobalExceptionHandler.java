package com.flowmesh.supplier.api;

import com.flowmesh.common.api.ErrorResponse;
import com.flowmesh.supplier.application.IdempotencyKeyConflictException;
import com.flowmesh.supplier.application.SupplierApplicationNotFoundException;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.List;

/**
 * supplier 服务全局异常处理，统一映射为 {@link ErrorResponse}。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
        MethodArgumentNotValidException exception,
        HttpServletRequest request
    ) {
        List<String> details = exception.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .toList();
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse(
                "VALIDATION_ERROR",
                "请求参数校验失败。",
                traceId(request),
                details
            ));
    }

    @ExceptionHandler(MissingIdempotencyKeyException.class)
    public ResponseEntity<ErrorResponse> handleMissingIdempotencyKey(
        MissingIdempotencyKeyException exception,
        HttpServletRequest request
    ) {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.of(
                "MISSING_IDEMPOTENCY_KEY",
                "Idempotency-Key 请求头缺失。",
                traceId(request)
            ));
    }

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyConflict(
        IdempotencyKeyConflictException exception,
        HttpServletRequest request
    ) {
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ErrorResponse.of(
                "IDEMPOTENCY_KEY_CONFLICT",
                "Idempotency-Key 已用于不同的请求体。",
                traceId(request)
            ));
    }

    /**
     * 映射超长幂等键。
     *
     * @param exception 幂等键格式异常
     * @param request 当前请求
     * @return 400 错误响应
     */
    @ExceptionHandler(InvalidIdempotencyKeyException.class)
    public ResponseEntity<ErrorResponse> handleInvalidIdempotencyKey(
        InvalidIdempotencyKeyException exception,
        HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.of(
            "INVALID_IDEMPOTENCY_KEY", "Idempotency-Key 不能超过 128 个字符。", traceId(request)
        ));
    }

    /**
     * 映射并发状态更新冲突。
     *
     * @param exception 乐观锁异常
     * @param request 当前请求
     * @return 409 错误响应
     */
    @ExceptionHandler({OptimisticLockException.class, ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<ErrorResponse> handleOptimisticLock(
        RuntimeException exception,
        HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(
            "STATE_VERSION_CONFLICT", "申请状态已发生变化，请刷新后重试。", traceId(request)
        ));
    }

    /**
     * 将当前租户不可见的申请映射为 404。
     *
     * @param exception 申请不存在异常
     * @param request 当前请求
     * @return 404 错误响应
     */
    @ExceptionHandler(SupplierApplicationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleApplicationNotFound(
        SupplierApplicationNotFoundException exception,
        HttpServletRequest request
    ) {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse.of(
                "SUPPLIER_APPLICATION_NOT_FOUND",
                "供应商申请不存在。",
                traceId(request)
            ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
        Exception exception,
        HttpServletRequest request
    ) {
        log.error("未预期异常", exception);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse.of(
                "INTERNAL_ERROR",
                "服务内部错误。",
                traceId(request)
            ));
    }

    private static String traceId(HttpServletRequest request) {
        String traceId = request.getHeader("X-Trace-Id");
        return traceId != null ? traceId : "";
    }
}

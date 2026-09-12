package com.flowmesh.supplier.api;

import com.flowmesh.common.api.ErrorResponse;
import com.flowmesh.common.security.TraceIdFilter;
import com.flowmesh.supplier.application.IdempotencyKeyConflictException;
import com.flowmesh.supplier.application.SupplierApplicationNotFoundException;
import com.flowmesh.supplier.application.DeadLetterEventNotFoundException;
import com.flowmesh.supplier.application.DocumentAccessDeniedException;
import com.flowmesh.supplier.application.DocumentInfectedException;
import com.flowmesh.supplier.application.DocumentUploadClosedException;
import com.flowmesh.supplier.application.InvalidReplayException;
import com.flowmesh.supplier.application.SupplierDocumentNotFoundException;
import com.flowmesh.supplier.storage.DocumentValidationException;
import com.flowmesh.supplier.storage.FileScanUnavailableException;
import com.flowmesh.supplier.storage.ObjectStorageException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

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

    /**
     * 处理请求体格式、查询参数类型或必填参数错误。
     *
     * @param exception 请求绑定异常
     * @param request HTTP 请求
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
        return ResponseEntity.badRequest().body(ErrorResponse.of(
            "INVALID_REQUEST", "请求格式或参数类型不正确。", traceId(request)
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
    @ExceptionHandler(OptimisticLockingFailureException.class)
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

    /**
     * 映射不存在的死信事件。
     *
     * @param exception 死信不存在异常
     * @param request 当前请求
     * @return 404 错误响应
     */
    @ExceptionHandler(DeadLetterEventNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleDeadLetterNotFound(
        DeadLetterEventNotFoundException exception,
        HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(
            "DEAD_LETTER_NOT_FOUND", "可重放的死信事件不存在。", traceId(request)
        ));
    }

    /**
     * 映射不可解析的死信事件。
     *
     * @param exception 重放格式异常
     * @param request 当前请求
     * @return 400 错误响应
     */
    @ExceptionHandler(InvalidReplayException.class)
    public ResponseEntity<ErrorResponse> handleInvalidReplay(
        InvalidReplayException exception,
        HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.of(
            "INVALID_REPLAY_EVENT", "死信事件载荷不是有效的事件信封。", traceId(request)
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

    /**
     * 映射材料参数校验失败。
     *
     * @param exception 材料校验异常
     * @param request 当前请求
     * @return 400 错误响应
     */
    @ExceptionHandler(DocumentValidationException.class)
    public ResponseEntity<ErrorResponse> handleDocumentValidation(
        DocumentValidationException exception,
        HttpServletRequest request
    ) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(
            "INVALID_DOCUMENT", exception.getMessage(), traceId(request)
        ));
    }

    /**
     * 映射 Web 容器层面的上传大小限制。
     *
     * @param exception 上传大小异常
     * @param request 当前请求
     * @return 413 错误响应
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(
        MaxUploadSizeExceededException exception,
        HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(ErrorResponse.of(
            "DOCUMENT_TOO_LARGE", "上传文件超过大小限制。", traceId(request)
        ));
    }

    /**
     * 映射恶意文件。
     *
     * @param exception 恶意文件异常
     * @param request 当前请求
     * @return 422 错误响应
     */
    @ExceptionHandler(DocumentInfectedException.class)
    public ResponseEntity<ErrorResponse> handleDocumentInfected(
        DocumentInfectedException exception,
        HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ErrorResponse.of(
            "INFECTED_DOCUMENT", "文件安全扫描未通过。", traceId(request)
        ));
    }

    /**
     * 映射已关闭申请的材料上传。
     *
     * @param exception 申请已关闭异常
     * @param request 当前请求
     * @return 409 错误响应
     */
    @ExceptionHandler(DocumentUploadClosedException.class)
    public ResponseEntity<ErrorResponse> handleDocumentUploadClosed(
        DocumentUploadClosedException exception,
        HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(
            "DOCUMENT_UPLOAD_CLOSED", "申请已进入启用状态，不能继续上传材料。", traceId(request)
        ));
    }

    /**
     * 映射材料访问权限错误。
     *
     * @param exception 权限异常
     * @param request 当前请求
     * @return 403 错误响应
     */
    @ExceptionHandler(DocumentAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleDocumentAccessDenied(
        DocumentAccessDeniedException exception,
        HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.of(
            "DOCUMENT_ACCESS_DENIED", "当前用户无权访问该申请材料。", traceId(request)
        ));
    }

    /**
     * 映射材料不存在。
     *
     * @param exception 材料不存在异常
     * @param request 当前请求
     * @return 404 错误响应
     */
    @ExceptionHandler(SupplierDocumentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleDocumentNotFound(
        SupplierDocumentNotFoundException exception,
        HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(
            "DOCUMENT_NOT_FOUND", "供应商材料不存在。", traceId(request)
        ));
    }

    /**
     * 映射对象存储或扫描引擎暂时不可用。
     *
     * @param exception 基础设施异常
     * @param request 当前请求
     * @return 503 错误响应
     */
    @ExceptionHandler({ObjectStorageException.class, FileScanUnavailableException.class})
    public ResponseEntity<ErrorResponse> handleDocumentInfrastructure(
        RuntimeException exception,
        HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ErrorResponse.of(
            "DOCUMENT_INFRASTRUCTURE_UNAVAILABLE", "材料安全扫描或对象存储暂时不可用。", traceId(request)
        ));
    }

    private static String traceId(HttpServletRequest request) {
        return TraceIdFilter.currentTraceId(request);
    }
}

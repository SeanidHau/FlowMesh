package com.flowmesh.workflow.api;

import com.flowmesh.common.api.ErrorResponse;
import com.flowmesh.common.security.TraceIdFilter;
import com.flowmesh.workflow.application.WorkflowInstanceNotFoundException;
import com.flowmesh.workflow.application.WorkflowTaskConflictException;
import com.flowmesh.workflow.application.WorkflowTaskForbiddenException;
import com.flowmesh.workflow.application.DeadLetterEventNotFoundException;
import com.flowmesh.workflow.application.InvalidReplayException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * workflow 服务 API 异常映射。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 映射请求字段校验错误。
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
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "请求格式或参数类型不正确。", request);
    }

    /**
     * 映射当前租户不存在流程实例。
     *
     * @param exception 业务异常
     * @param request 当前请求
     * @return 404 错误响应
     */
    @ExceptionHandler(WorkflowInstanceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
        WorkflowInstanceNotFoundException exception,
        HttpServletRequest request
    ) {
        return response(HttpStatus.NOT_FOUND, "WORKFLOW_INSTANCE_NOT_FOUND", "流程实例不存在。", request);
    }

    /**
     * 映射任务状态冲突。
     *
     * @param exception 业务异常
     * @param request 当前请求
     * @return 409 错误响应
     */
    @ExceptionHandler(WorkflowTaskConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(
        WorkflowTaskConflictException exception,
        HttpServletRequest request
    ) {
        return response(HttpStatus.CONFLICT, "WORKFLOW_TASK_CONFLICT", "当前流程状态不允许完成该任务。", request);
    }

    /**
     * 映射任务角色不足。
     *
     * @param exception 业务异常
     * @param request 当前请求
     * @return 403 错误响应
     */
    @ExceptionHandler(WorkflowTaskForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(
        WorkflowTaskForbiddenException exception,
        HttpServletRequest request
    ) {
        return response(HttpStatus.FORBIDDEN, "WORKFLOW_TASK_FORBIDDEN", "当前角色不能完成该任务。", request);
    }

    /**
     * 映射流程并发更新冲突。
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
        return response(HttpStatus.CONFLICT, "WORKFLOW_STATE_CONFLICT", "流程状态已发生变化，请刷新后重试。", request);
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
        return response(HttpStatus.NOT_FOUND, "DEAD_LETTER_NOT_FOUND", "可重放的死信事件不存在。", request);
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
        return response(HttpStatus.BAD_REQUEST, "INVALID_REPLAY_EVENT", "死信事件载荷不是有效的事件信封。", request);
    }

    /**
     * 处理未预期异常，避免向客户端暴露堆栈、SQL 或依赖错误详情。
     *
     * @param exception 未预期异常
     * @param request HTTP 请求
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

    private ResponseEntity<ErrorResponse> response(
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

    private ResponseEntity<ErrorResponse> response(
        HttpStatus status,
        String code,
        String message,
        HttpServletRequest request
    ) {
        return response(status, code, message, request, List.of());
    }
}

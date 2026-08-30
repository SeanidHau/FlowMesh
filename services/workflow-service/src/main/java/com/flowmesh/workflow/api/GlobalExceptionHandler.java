package com.flowmesh.workflow.api;

import com.flowmesh.common.api.ErrorResponse;
import com.flowmesh.workflow.application.WorkflowInstanceNotFoundException;
import com.flowmesh.workflow.application.WorkflowTaskConflictException;
import com.flowmesh.workflow.application.WorkflowTaskForbiddenException;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * workflow 服务 API 异常映射。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

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
    @ExceptionHandler({OptimisticLockException.class, ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<ErrorResponse> handleOptimisticLock(
        RuntimeException exception,
        HttpServletRequest request
    ) {
        return response(HttpStatus.CONFLICT, "WORKFLOW_STATE_CONFLICT", "流程状态已发生变化，请刷新后重试。", request);
    }

    private ResponseEntity<ErrorResponse> response(
        HttpStatus status,
        String code,
        String message,
        HttpServletRequest request,
        List<String> details
    ) {
        return ResponseEntity.status(status).body(new ErrorResponse(
            code, message, request.getHeader("X-Trace-Id") == null
                ? "" : request.getHeader("X-Trace-Id"), details
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

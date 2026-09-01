package com.flowmesh.iam.api;

import com.flowmesh.common.api.ErrorResponse;
import com.flowmesh.common.security.TraceIdFilter;
import com.flowmesh.iam.application.auth.InvalidCredentialsException;
import com.flowmesh.iam.application.auth.LoginRateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * IAM 服务全局异常处理，统一映射为 {@link ErrorResponse}。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理请求参数校验失败。
     *
     * @param exception 校验异常
     * @param request HTTP 请求
     * @return 400 ErrorResponse
     */
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
     * 处理凭证无效异常，统一返回 401。
     *
     * @param exception 凭证异常
     * @param request HTTP 请求
     * @return 401 ErrorResponse
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(
        InvalidCredentialsException exception,
        HttpServletRequest request
    ) {
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse.of(
                "INVALID_CREDENTIALS",
                "用户名或密码错误。",
                traceId(request)
            ));
    }

    /**
     * 处理登录限流异常。
     *
     * @param exception 登录限流异常
     * @param request HTTP 请求
     * @return 429 ErrorResponse
     */
    @ExceptionHandler(LoginRateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleLoginRateLimit(
        LoginRateLimitExceededException exception,
        HttpServletRequest request
    ) {
        return ResponseEntity
            .status(HttpStatus.TOO_MANY_REQUESTS)
            .header("Retry-After", "60")
            .body(ErrorResponse.of(
                "LOGIN_RATE_LIMITED",
                "登录尝试过于频繁，请稍后再试。",
                traceId(request)
            ));
    }

    /**
     * 处理未预期的异常，返回 500。
     *
     * @param exception 未捕获异常
     * @param request HTTP 请求
     * @return 500 ErrorResponse
     */
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
        return TraceIdFilter.currentTraceId(request);
    }
}

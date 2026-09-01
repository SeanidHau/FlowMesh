package com.flowmesh.iam.api;

import com.flowmesh.iam.api.dto.LoginRequest;
import com.flowmesh.iam.api.dto.LogoutRequest;
import com.flowmesh.iam.api.dto.RefreshRequest;
import com.flowmesh.iam.api.dto.TokenResponse;
import com.flowmesh.iam.application.auth.AuthApplicationService;
import com.flowmesh.iam.application.auth.LoginRateLimiter;
import com.flowmesh.common.security.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * IAM 认证端点：登录、刷新令牌、登出。
 *
 * <p>这些端点在安全链中已放行（permitAll），认证逻辑由 {@link AuthApplicationService} 处理。</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthApplicationService authApplicationService;
    private final LoginRateLimiter loginRateLimiter;

    /**
     * 创建认证控制器。
     *
     * @param authApplicationService 认证应用服务
     * @param loginRateLimiter 登录限流器
     */
    public AuthController(
        AuthApplicationService authApplicationService,
        LoginRateLimiter loginRateLimiter
    ) {
        this.authApplicationService = authApplicationService;
        this.loginRateLimiter = loginRateLimiter;
    }

    /**
     * 用户登录，返回令牌对。
     *
     * @param request 登录请求
     * @param httpRequest HTTP 请求
     * @return Access Token 和 Refresh Token
     */
    @PostMapping("/login")
    public TokenResponse login(
        @Valid @RequestBody LoginRequest request,
        HttpServletRequest httpRequest
    ) {
        var attempt = new LoginRateLimiter.LoginAttempt(
            request.tenantId(), request.username(), httpRequest.getRemoteAddr()
        );
        loginRateLimiter.check(attempt);
        String traceId = TraceIdFilter.currentTraceId(httpRequest);
        var result = authApplicationService.login(
            request.tenantId(), request.username(), request.password(), traceId
        );
        loginRateLimiter.clearAccount(request.tenantId(), request.username());
        return new TokenResponse(result.accessToken(), result.refreshToken());
    }

    /**
     * 刷新令牌轮换，返回新的令牌对。
     *
     * @param request 刷新请求
     * @return 新的 Access Token 和 Refresh Token
     */
    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        var result = authApplicationService.refresh(request.refreshToken());
        return new TokenResponse(result.accessToken(), result.refreshToken());
    }

    /**
     * 撤销刷新令牌，完成登出。幂等，始终返回 204。
     *
     * @param request 登出请求
     * @param httpRequest HTTP 请求
     * @return 204 No Content
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
        @Valid @RequestBody LogoutRequest request,
        HttpServletRequest httpRequest
    ) {
        authApplicationService.logout(
            request.refreshToken(), TraceIdFilter.currentTraceId(httpRequest)
        );
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}

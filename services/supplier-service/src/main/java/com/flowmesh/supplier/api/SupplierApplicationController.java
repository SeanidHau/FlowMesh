package com.flowmesh.supplier.api;

import com.flowmesh.common.security.AuthPrincipal;
import com.flowmesh.supplier.api.dto.ApplicationResponse;
import com.flowmesh.supplier.api.dto.CreateApplicationRequest;
import com.flowmesh.supplier.application.SupplierApplicationService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 供应商申请控制器。
 *
 * <p>POST /api/v1/supplier-applications 创建申请，需认证且持有 APPLICANT 角色，
 * 必须携带 Idempotency-Key 请求头。</p>
 */
@RestController
@RequestMapping("/api/v1/supplier-applications")
public class SupplierApplicationController {

    private final SupplierApplicationService applicationService;

    /**
     * 创建申请控制器。
     *
     * @param applicationService 申请服务
     */
    public SupplierApplicationController(SupplierApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 创建供应商申请。
     *
     * @param principal 已认证主体
     * @param idempotencyKey 幂等键
     * @param request 创建请求
     * @param traceId 请求追踪标识
     * @return 首次响应或回放快照
     */
    @PostMapping
    public ResponseEntity<String> create(
        @AuthenticationPrincipal AuthPrincipal principal,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @Valid @RequestBody CreateApplicationRequest request,
        @RequestHeader(value = "X-Trace-Id", defaultValue = "") String traceId
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new MissingIdempotencyKeyException();
        }

        var result = applicationService.create(principal, idempotencyKey, request, traceId);

        return ResponseEntity
            .status(result.status())
            .contentType(MediaType.APPLICATION_JSON)
            .body(result.body());
    }

    /**
     * 查询当前租户下的供应商申请。
     *
     * @param principal 已认证主体
     * @param applicationId 申请标识
     * @return 申请当前状态
     */
    @GetMapping("/{applicationId}")
    public ApplicationResponse find(
        @AuthenticationPrincipal AuthPrincipal principal,
        @PathVariable UUID applicationId
    ) {
        return applicationService.find(principal, applicationId);
    }
}

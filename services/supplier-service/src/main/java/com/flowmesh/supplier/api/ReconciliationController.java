package com.flowmesh.supplier.api;

import com.flowmesh.common.security.AuthPrincipal;
import com.flowmesh.supplier.api.dto.ReconciliationResponse;
import com.flowmesh.supplier.application.ReconciliationService;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供供应商申请状态对账接口。
 */
@RestController
@RequestMapping("/api/v1/operations/reconciliation")
public class ReconciliationController {

    private final ReconciliationService service;

    /**
     * 创建对账控制器。
     *
     * @param service 对账应用服务
     */
    public ReconciliationController(ReconciliationService service) {
        this.service = service;
    }

    /**
     * 对账当前租户指定申请。
     *
     * @param principal 当前操作者
     * @param applicationId 申请标识
     * @param authorization 当前 Bearer Token
     * @return 对账结果
     */
    @GetMapping("/{applicationId}")
    public ReconciliationResponse reconcile(
        @AuthenticationPrincipal AuthPrincipal principal,
        @PathVariable UUID applicationId,
        @RequestHeader(value = "Authorization", defaultValue = "") String authorization
    ) {
        return service.reconcile(principal, applicationId, authorization);
    }
}

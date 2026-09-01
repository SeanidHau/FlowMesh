package com.flowmesh.workflow.api;

import com.flowmesh.common.security.AuthPrincipal;
import com.flowmesh.workflow.api.dto.WorkflowReconciliationSnapshot;
import com.flowmesh.workflow.application.WorkflowInstanceService;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 暴露 workflow 只读对账快照的内部接口。
 */
@RestController
@RequestMapping("/internal/v1/reconciliation/workflow-instances")
public class ReconciliationInternalController {

    private final WorkflowInstanceService service;

    /**
     * 创建内部对账控制器。
     *
     * @param service 流程实例服务
     */
    public ReconciliationInternalController(WorkflowInstanceService service) {
        this.service = service;
    }

    /**
     * 查询当前租户的流程对账快照。
     *
     * @param principal 当前操作者
     * @param applicationId 申请标识
     * @return workflow 快照
     */
    @GetMapping("/{applicationId}")
    public WorkflowReconciliationSnapshot snapshot(
        @AuthenticationPrincipal AuthPrincipal principal,
        @PathVariable UUID applicationId
    ) {
        return service.snapshot(principal.tenantId(), applicationId);
    }
}

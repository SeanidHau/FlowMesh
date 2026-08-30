package com.flowmesh.workflow.api;

import com.flowmesh.common.security.AuthPrincipal;
import com.flowmesh.workflow.api.dto.CompleteTaskRequest;
import com.flowmesh.workflow.api.dto.WorkflowInstanceResponse;
import com.flowmesh.workflow.application.WorkflowInstanceService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供流程实例查询和审批节点推进 API。
 */
@RestController
@RequestMapping("/api/v1/workflow-instances")
public class WorkflowInstanceController {

    private final WorkflowInstanceService service;

    /**
     * 创建流程实例控制器。
     *
     * @param service 流程实例应用服务
     */
    public WorkflowInstanceController(WorkflowInstanceService service) {
        this.service = service;
    }

    /**
     * 查询当前租户下的流程实例。
     *
     * @param principal 已认证主体
     * @param applicationId 申请标识
     * @return 流程实例
     */
    @GetMapping("/{applicationId}")
    public WorkflowInstanceResponse find(
        @AuthenticationPrincipal AuthPrincipal principal,
        @PathVariable UUID applicationId
    ) {
        return WorkflowInstanceResponse.from(service.find(principal.tenantId(), applicationId));
    }

    /**
     * 完成当前审批节点并推进流程。
     *
     * @param principal 已认证主体
     * @param applicationId 申请标识
     * @param request 任务完成请求
     * @return 推进后的流程实例
     */
    @PostMapping("/{applicationId}/tasks")
    public WorkflowInstanceResponse completeTask(
        @AuthenticationPrincipal AuthPrincipal principal,
        @PathVariable UUID applicationId,
        @Valid @RequestBody CompleteTaskRequest request,
        @RequestHeader(value = "X-Trace-Id", defaultValue = "") String traceId
    ) {
        return WorkflowInstanceResponse.from(
            service.completeTask(principal, applicationId, request.taskKey(), traceId)
        );
    }
}

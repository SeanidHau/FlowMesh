package com.flowmesh.supplier.api;

import com.flowmesh.common.security.AuthPrincipal;
import com.flowmesh.common.security.TraceIdFilter;
import com.flowmesh.supplier.api.dto.DeadLetterEventResponse;
import com.flowmesh.supplier.api.dto.ReplayOutboxRequest;
import com.flowmesh.supplier.api.dto.ReplayOutboxResponse;
import com.flowmesh.supplier.application.OutboxOperationsService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * supplier Outbox 运维接口。
 *
 * <p>控制器路径由安全配置限制为 {@code OPERATIONS} 角色，并且所有查询都使用签名 JWT
 * 中的租户标识，不接受客户端传入租户。</p>
 */
@RestController
@RequestMapping("/api/v1/operations/outbox")
public class OutboxOperationsController {

    private final OutboxOperationsService service;

    /**
     * 创建 Outbox 运维控制器。
     *
     * @param service 运维应用服务
     */
    public OutboxOperationsController(OutboxOperationsService service) {
        this.service = service;
    }

    /**
     * 查询当前租户的死信事件。
     *
     * @param principal 当前操作者
     * @param eventType 事件类型，可为空
     * @param aggregateId 聚合标识，可为空
     * @return 死信事件列表
     */
    @GetMapping("/dead-letters")
    public List<DeadLetterEventResponse> list(
        @AuthenticationPrincipal AuthPrincipal principal,
        @RequestParam(required = false) String eventType,
        @RequestParam(required = false) UUID aggregateId
    ) {
        return service.listDeadLetters(principal, eventType, aggregateId);
    }

    /**
     * 受控重放一条死信事件。
     *
     * @param principal 当前操作者
     * @param eventId 原死信标识
     * @param request 重放请求
     * @param httpRequest HTTP 请求
     * @return 新事件标识和状态
     */
    @PostMapping("/{eventId}/replay")
    public ReplayOutboxResponse replay(
        @AuthenticationPrincipal AuthPrincipal principal,
        @PathVariable UUID eventId,
        @Valid @RequestBody ReplayOutboxRequest request,
        HttpServletRequest httpRequest
    ) {
        return service.replay(
            principal, eventId, request.reason(), TraceIdFilter.currentTraceId(httpRequest)
        );
    }
}

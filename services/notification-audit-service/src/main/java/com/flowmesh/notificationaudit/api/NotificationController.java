package com.flowmesh.notificationaudit.api;

import com.flowmesh.common.security.AuthPrincipal;
import com.flowmesh.notificationaudit.application.NotificationAuditService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供当前用户的站内通知查询接口。
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationAuditService service;

    /**
     * 创建通知查询控制器。
     *
     * @param service 通知审计服务
     */
    public NotificationController(NotificationAuditService service) {
        this.service = service;
    }

    /**
     * 查询当前租户用户的最近通知。
     *
     * @param principal 已认证主体
     * @param limit 返回上限
     * @return 通知列表
     */
    @GetMapping
    public List<NotificationResponse> list(
        @AuthenticationPrincipal AuthPrincipal principal,
        @RequestParam(defaultValue = "20") int limit
    ) {
        return service.findNotifications(principal.tenantId(), principal.userId(), limit)
            .stream()
            .map(NotificationResponse::from)
            .toList();
    }

    /**
     * 将当前用户的通知标记为已读。
     *
     * @param principal 已认证主体
     * @param notificationId 通知标识
     * @return 空响应
     */
    @PutMapping("/{notificationId}/read")
    public ResponseEntity<Void> markAsRead(
        @AuthenticationPrincipal AuthPrincipal principal,
        @PathVariable UUID notificationId
    ) {
        service.markNotificationAsRead(principal.tenantId(), principal.userId(), notificationId);
        return ResponseEntity.noContent().build();
    }
}

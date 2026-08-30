package com.flowmesh.iam.application.auth;

import com.flowmesh.iam.domain.audit.AuditEvent;
import com.flowmesh.iam.repository.AuditEventRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 写入认证失败审计，使用独立事务避免业务异常回滚安全事件。
 */
@Service
public class SecurityAuditWriter {

    private final AuditEventRepository repository;

    /**
     * 创建安全审计写入器。
     *
     * @param repository 审计事件仓储
     */
    public SecurityAuditWriter(AuditEventRepository repository) {
        this.repository = repository;
    }

    /**
     * 记录登录失败，不保存密码。
     *
     * @param tenantId 请求租户
     * @param username 请求用户名
     * @param traceId 链路追踪标识
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLoginFailure(String tenantId, String username, String traceId) {
        repository.save(new AuditEvent(
            tenantId, null, "LOGIN", "USER", username, "FAILURE", traceId
        ));
    }
}

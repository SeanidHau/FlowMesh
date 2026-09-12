package com.flowmesh.iam.application.auth;

import com.flowmesh.iam.domain.audit.AuditEvent;
import com.flowmesh.iam.repository.AuditEventRepository;
import com.flowmesh.iam.repository.TenantRepository;
import com.flowmesh.iam.rls.TenantRlsInitializer;
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
    private final TenantRepository tenantRepository;
    private final TenantRlsInitializer tenantRlsInitializer;

    /**
     * 创建安全审计写入器。
     *
     * @param repository 审计事件仓储
     * @param tenantRepository 租户仓储
     * @param tenantRlsInitializer 租户上下文初始化器
     */
    public SecurityAuditWriter(
        AuditEventRepository repository,
        TenantRepository tenantRepository,
        TenantRlsInitializer tenantRlsInitializer
    ) {
        this.repository = repository;
        this.tenantRepository = tenantRepository;
        this.tenantRlsInitializer = tenantRlsInitializer;
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
        if (tenantRepository.findById(tenantId).isEmpty()) {
            return;
        }
        tenantRlsInitializer.initialize(tenantId);
        repository.save(new AuditEvent(
            tenantId, null, "LOGIN", "USER", username, "FAILURE", traceId
        ));
    }
}

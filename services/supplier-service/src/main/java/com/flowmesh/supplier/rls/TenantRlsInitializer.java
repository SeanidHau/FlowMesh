package com.flowmesh.supplier.rls;

import com.flowmesh.common.security.AuthPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 在业务事务内设置 app.tenant_id，使 PostgreSQL RLS 策略生效。
 *
 * <p>调用方在 {@code @Transactional} 方法内、执行业务查询前调用 {@link #initializeTenant()}。
 * {@code set_config('app.tenant_id', ?, true)} 以事务级方式写入，同一事务内所有后续查询
 * 均受 RLS 约束。租户值取自 SecurityContext 中 JWT 的 tenantId claim。</p>
 */
@Component
public class TenantRlsInitializer {

    private final TenantRlsMapper mapper;

    /**
     * 创建租户 RLS 初始化器。
     *
     * @param mapper MyBatis 租户上下文 Mapper
     */
    public TenantRlsInitializer(TenantRlsMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 从 SecurityContext 提取租户标识并写入当前事务的 app.tenant_id。
     */
    public void initializeTenant() {
        AuthPrincipal principal = currentPrincipal();
        if (principal == null) {
            return;
        }
        initializeTenant(principal.tenantId());
    }

    /**
     * 使用受信事件中的租户标识初始化当前事务。
     *
     * @param tenantId 事件信封中的租户标识
     */
    public void initializeTenant(String tenantId) {
        mapper.setTenant(tenantId);
    }

    private AuthPrincipal currentPrincipal() {
        SecurityContext context = SecurityContextHolder.getContext();
        if (context.getAuthentication() == null
            || !(context.getAuthentication().getPrincipal() instanceof AuthPrincipal principal)) {
            return null;
        }
        return principal;
    }
}

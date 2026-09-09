package com.flowmesh.notificationaudit.rls;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 在业务事务中设置通知审计 Schema 的 RLS 上下文。
 */
@Component
public class TenantRlsInitializer {

    private final TenantRlsMapper mapper;

    /**
     * 创建 RLS 上下文初始化器。
     *
     * @param mapper RLS SQL 映射器
     */
    public TenantRlsInitializer(TenantRlsMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 使用当前事务连接设置租户上下文。
     *
     * @param tenantId 可信租户标识
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void initialize(String tenantId) {
        mapper.setTenant(tenantId);
    }
}

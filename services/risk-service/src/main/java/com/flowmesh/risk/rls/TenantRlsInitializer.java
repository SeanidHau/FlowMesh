package com.flowmesh.risk.rls;

import org.springframework.stereotype.Component;

/**
 * 在风险服务事务中初始化 PostgreSQL RLS 租户上下文。
 */
@Component
public class TenantRlsInitializer {

    private final TenantRlsMapper mapper;

    /**
     * 创建初始化器。
     *
     * @param mapper RLS Mapper
     */
    public TenantRlsInitializer(TenantRlsMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 设置当前事务租户。
     *
     * @param tenantId 租户标识
     */
    public void initialize(String tenantId) {
        mapper.setTenant(tenantId);
    }
}

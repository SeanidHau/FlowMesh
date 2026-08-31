package com.flowmesh.workflow.rls;

import org.springframework.stereotype.Component;

/**
 * 在当前数据库事务中设置 workflow 服务的租户上下文。
 */
@Component
public class TenantRlsInitializer {

    private final TenantRlsMapper mapper;

    /**
     * 创建租户上下文初始化器。
     *
     * @param mapper MyBatis 租户上下文 Mapper
     */
    public TenantRlsInitializer(TenantRlsMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 设置事务级租户标识，供 PostgreSQL RLS 策略使用。
     *
     * @param tenantId 可信租户标识
     */
    public void initialize(String tenantId) {
        mapper.setTenant(tenantId);
    }
}

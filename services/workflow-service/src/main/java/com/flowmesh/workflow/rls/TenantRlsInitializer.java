package com.flowmesh.workflow.rls;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

/**
 * 在当前数据库事务中设置 workflow 服务的租户上下文。
 */
@Component
public class TenantRlsInitializer {

    private final EntityManager entityManager;

    /**
     * 创建租户上下文初始化器。
     *
     * @param entityManager 当前 JPA 实体管理器
     */
    public TenantRlsInitializer(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * 设置事务级租户标识，供 PostgreSQL RLS 策略使用。
     *
     * @param tenantId 可信租户标识
     */
    public void initialize(String tenantId) {
        entityManager.createNativeQuery(
                "SELECT set_config('app.tenant_id', :tenantId, true)"
            )
            .setParameter("tenantId", tenantId)
            .getSingleResult();
    }
}

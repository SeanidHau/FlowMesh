package com.flowmesh.iam.rls;

import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 在 IAM 业务事务中初始化 PostgreSQL RLS 租户上下文。
 *
 * <p>登录、刷新和登出均在认证前或认证过程中使用请求提供的租户标识；Refresh Token
 * 表同时保存租户归属，因此即使令牌本身尚未解析出用户，也不会绕过 RLS。</p>
 */
@Component
public class TenantRlsInitializer {

    private final TenantRlsMapper mapper;

    /**
     * 创建 IAM 租户上下文初始化器。
     *
     * @param mapper 租户上下文 Mapper
     */
    public TenantRlsInitializer(TenantRlsMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 设置当前事务的租户标识。
     *
     * @param tenantId 可信租户标识
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void initialize(String tenantId) {
        mapper.setTenant(Objects.requireNonNull(tenantId, "tenantId must not be null"));
    }
}

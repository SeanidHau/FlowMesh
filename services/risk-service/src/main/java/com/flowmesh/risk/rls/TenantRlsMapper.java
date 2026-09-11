package com.flowmesh.risk.rls;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

/**
 * 设置风险服务当前事务租户上下文。
 */
@Mapper
public interface TenantRlsMapper {

    /**
     * 设置事务级租户配置。
     *
     * @param tenantId 租户标识
     * @return PostgreSQL {@code set_config} 返回的租户标识
     */
    @Select("SELECT set_config('app.tenant_id', #{tenantId}, true)")
    String setTenant(@Param("tenantId") String tenantId);
}

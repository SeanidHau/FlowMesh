package com.flowmesh.supplier.rls;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 使用 MyBatis 在当前事务连接中设置 PostgreSQL RLS 租户上下文。
 */
@Mapper
public interface TenantRlsMapper {

    /**
     * 设置事务级租户标识。
     *
     * @param tenantId 可信租户标识
     * @return PostgreSQL 返回的配置值
     */
    @Select("SELECT set_config('app.tenant_id', #{tenantId}, true)")
    String setTenant(@Param("tenantId") String tenantId);
}

package com.flowmesh.notificationaudit.rls;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 设置当前事务的 PostgreSQL RLS 租户上下文。
 */
@Mapper
public interface TenantRlsMapper {

    /**
     * 将租户标识写入当前数据库事务。
     *
     * @param tenantId 租户标识
     * @return PostgreSQL {@code set_config} 返回的租户标识
     */
    @Select("SELECT set_config('app.tenant_id', #{tenantId}, true)")
    String setTenant(@Param("tenantId") String tenantId);
}

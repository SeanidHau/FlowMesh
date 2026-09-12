package com.flowmesh.iam.rls;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 使用当前事务连接设置 IAM 的 PostgreSQL 租户上下文。
 */
@Mapper
public interface TenantRlsMapper {

    /**
     * 写入事务级租户标识。
     *
     * @param tenantId 可信租户标识
     */
    @Select("SELECT set_config('app.tenant_id', #{tenantId}, true)")
    String setTenant(@Param("tenantId") String tenantId);
}

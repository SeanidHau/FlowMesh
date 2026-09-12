package com.flowmesh.iam.repository;

import com.flowmesh.iam.domain.tenant.Tenant;
import java.util.Optional;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/**
 * 使用 MyBatis 访问 IAM 租户表。
 */
@Mapper
public interface TenantRepository {

    /**
     * 插入租户。
     *
     * @param tenant 待保存租户
     * @return 保存后的租户对象
     */
    default Tenant saveAndFlush(Tenant tenant) {
        insert(tenant);
        return tenant;
    }

    /**
     * 按租户标识查询租户。
     *
     * @param id 租户标识
     * @return 租户；不存在时为空
     */
    Optional<Tenant> findById(String id);

    /**
     * 查询所有租户标识，供需要逐租户设置 RLS 上下文的维护任务使用。
     *
     * @return 租户标识列表
     */
    List<String> findAllIds();

    /**
     * 插入租户记录。
     *
     * @param tenant 待保存租户
     * @return 受影响行数
     */
    int insert(Tenant tenant);
}

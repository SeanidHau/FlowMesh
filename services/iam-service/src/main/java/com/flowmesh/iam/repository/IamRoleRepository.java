package com.flowmesh.iam.repository;

import com.flowmesh.iam.domain.role.IamRole;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 使用 MyBatis 访问 IAM 角色表。
 */
@Mapper
public interface IamRoleRepository {

    /**
     * 按角色代码查询角色。
     *
     * @param code 大写角色代码
     * @return 角色；不存在时为空
     */
    Optional<IamRole> findByCode(@Param("code") String code);

    /**
     * 判断角色代码是否已存在。
     *
     * @param code 大写角色代码
     * @return 存在时为 {@code true}
     */
    boolean existsByCode(@Param("code") String code);

    /**
     * 插入角色。
     *
     * @param role 待保存角色
     * @return 保存后的角色对象
     */
    default IamRole saveAndFlush(IamRole role) {
        insert(role);
        return role;
    }

    /**
     * 插入角色记录。
     *
     * @param role 待保存角色
     * @return 受影响行数
     */
    int insert(IamRole role);
}

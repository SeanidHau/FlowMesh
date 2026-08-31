package com.flowmesh.iam.repository;

import com.flowmesh.iam.domain.role.UserRole;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 使用 MyBatis 访问用户角色关联表。
 */
@Mapper
public interface UserRoleRepository {

    /**
     * 查询指定用户的全部角色授予关系。
     *
     * @param userId 用户标识
     * @return 用户角色关系列表
     */
    List<UserRole> findAllByUser_Id(@Param("userId") UUID userId);

    /**
     * 判断用户是否拥有指定角色。
     *
     * @param userId 用户标识
     * @param roleCode 角色代码
     * @return 已拥有角色时为 {@code true}
     */
    boolean existsByUser_IdAndRole_Code(
        @Param("userId") UUID userId,
        @Param("roleCode") String roleCode
    );

    /**
     * 插入用户角色关系。
     *
     * @param userRole 待保存关系
     * @return 保存后的关系对象
     */
    default UserRole saveAndFlush(UserRole userRole) {
        insert(userRole);
        return userRole;
    }

    /**
     * 插入用户角色关系记录。
     *
     * @param userRole 待保存关系
     * @return 受影响行数
     */
    int insert(UserRole userRole);
}

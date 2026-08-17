package com.flowmesh.iam.repository;

import com.flowmesh.iam.domain.role.UserRole;
import com.flowmesh.iam.domain.role.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * 管理用户与角色授予关系的持久化访问。
 */
public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    /**
     * 查询指定用户的全部角色授予关系。
     *
     * @param userId 用户标识
     * @return 用户角色关系列表
     */
    List<UserRole> findAllByUser_Id(UUID userId);

    /**
     * 判断用户是否拥有指定角色。
     *
     * @param userId 用户标识
     * @param roleCode 大写角色代码
     * @return 已拥有角色时为 {@code true}
     */
    boolean existsByUser_IdAndRole_Code(UUID userId, String roleCode);
}

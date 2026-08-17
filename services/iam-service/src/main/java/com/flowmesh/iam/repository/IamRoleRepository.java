package com.flowmesh.iam.repository;

import com.flowmesh.iam.domain.role.IamRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * 管理 IAM 角色聚合的持久化访问。
 */
public interface IamRoleRepository extends JpaRepository<IamRole, UUID> {

    /**
     * 按已归一化角色代码查询角色。
     *
     * @param code 大写角色代码
     * @return 匹配的角色；不存在时为空
     */
    Optional<IamRole> findByCode(String code);

    /**
     * 判断角色代码是否已存在。
     *
     * @param code 大写角色代码
     * @return 存在时为 {@code true}
     */
    boolean existsByCode(String code);
}

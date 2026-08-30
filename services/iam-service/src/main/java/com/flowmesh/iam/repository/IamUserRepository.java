package com.flowmesh.iam.repository;

import com.flowmesh.iam.domain.user.IamUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 管理 IAM 用户聚合的持久化访问。
 */
public interface IamUserRepository extends JpaRepository<IamUser, UUID> {

    /**
     * 按租户和已归一化用户名查询用户。
     *
     * @param tenantId 租户标识
     * @param username 已归一化的登录用户名
     * @return 匹配的用户；不存在时为空
     */
    Optional<IamUser> findByTenant_IdAndUsername(String tenantId, String username);

    /**
     * 判断租户内是否已存在指定用户名。
     *
     * @param tenantId 租户标识
     * @param username 已归一化的登录用户名
     * @return 存在时为 {@code true}
     */
    boolean existsByTenant_IdAndUsername(String tenantId, String username);

}

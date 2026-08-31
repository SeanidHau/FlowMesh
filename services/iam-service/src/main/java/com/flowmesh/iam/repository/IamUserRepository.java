package com.flowmesh.iam.repository;

import com.flowmesh.iam.domain.user.IamUser;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 使用 MyBatis 访问 IAM 用户表。
 */
@Mapper
public interface IamUserRepository {

    /**
     * 按租户和用户名查询用户，并加载租户信息。
     *
     * @param tenantId 租户标识
     * @param username 已归一化的登录用户名
     * @return 匹配用户；不存在时为空
     */
    Optional<IamUser> findByTenant_IdAndUsername(
        @Param("tenantId") String tenantId,
        @Param("username") String username
    );

    /**
     * 判断租户内是否已存在指定用户名。
     *
     * @param tenantId 租户标识
     * @param username 已归一化的登录用户名
     * @return 存在时为 {@code true}
     */
    boolean existsByTenant_IdAndUsername(
        @Param("tenantId") String tenantId,
        @Param("username") String username
    );

    /**
     * 插入用户。
     *
     * @param user 待保存用户
     * @return 保存后的用户对象
     */
    default IamUser saveAndFlush(IamUser user) {
        insert(user);
        return user;
    }

    /**
     * 更新用户最近登录时间和版本。
     *
     * @param user 已完成登录的用户
     * @return 受影响行数
     */
    int updateLastLogin(IamUser user);

    /**
     * 插入用户记录。
     *
     * @param user 待保存用户
     * @return 受影响行数
     */
    int insert(IamUser user);

    /**
     * 按用户标识查询用户。
     *
     * @param id 用户标识
     * @return 用户；不存在时为空
     */
    Optional<IamUser> findById(@Param("id") UUID id);
}

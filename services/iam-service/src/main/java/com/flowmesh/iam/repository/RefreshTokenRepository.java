package com.flowmesh.iam.repository;

import com.flowmesh.iam.domain.token.RefreshToken;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 使用 MyBatis 访问 Refresh Token 表。
 */
@Mapper
public interface RefreshTokenRepository {

    /**
     * 按令牌哈希查询刷新令牌。
     *
     * @param tokenHash 原始令牌的单向哈希
     * @return 刷新令牌；不存在时为空
     */
    Optional<RefreshToken> findByTokenHash(@Param("tokenHash") String tokenHash);

    /**
     * 在当前事务中锁定指定刷新令牌，并加载所属用户和租户。
     *
     * @param tokenHash 原始令牌的单向哈希
     * @return 被锁定刷新令牌；不存在时为空
     */
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    /**
     * 查询用户当前未撤销的刷新令牌。
     *
     * @param userId 用户标识
     * @return 未撤销令牌列表
     */
    List<RefreshToken> findAllByUser_IdAndRevokedAtIsNull(@Param("userId") UUID userId);

    /**
     * 插入刷新令牌。
     *
     * @param token 待保存令牌
     * @return 保存后的令牌对象
     */
    default RefreshToken saveAndFlush(RefreshToken token) {
        insert(token);
        return token;
    }

    /**
     * 更新刷新令牌轮换状态。
     *
     * @param token 已撤销并指向替代令牌的旧令牌
     * @return 受影响行数
     */
    int updateRotation(RefreshToken token);

    /**
     * 撤销刷新令牌。
     *
     * @param token 已撤销的刷新令牌
     * @return 受影响行数
     */
    int updateRevocation(RefreshToken token);

    /**
     * 插入刷新令牌记录。
     *
     * @param token 待保存令牌
     * @return 受影响行数
     */
    int insert(RefreshToken token);
}

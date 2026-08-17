package com.flowmesh.iam.repository;

import com.flowmesh.iam.domain.token.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 管理刷新令牌的持久化访问。
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /**
     * 按令牌哈希查询刷新令牌。
     *
     * @param tokenHash 原始令牌的单向哈希
     * @return 匹配的刷新令牌；不存在时为空
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * 锁定指定哈希对应的刷新令牌，并同时加载其所属用户。
     *
     * <p>调用方必须处于事务中，以保证并发刷新请求不能同时轮换同一令牌。</p>
     *
     * @param tokenHash 原始令牌的单向哈希
     * @return 被锁定的刷新令牌；不存在时为空
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT token
            FROM RefreshToken token
            JOIN FETCH token.user
            WHERE token.tokenHash = :tokenHash
            """)
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    /**
     * 查询用户当前未撤销的刷新令牌。
     *
     * @param userId 用户标识
     * @return 未撤销令牌列表
     */
    List<RefreshToken> findAllByUser_IdAndRevokedAtIsNull(UUID userId);
}

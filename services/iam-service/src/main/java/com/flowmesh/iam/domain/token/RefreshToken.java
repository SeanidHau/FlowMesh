package com.flowmesh.iam.domain.token;

import com.flowmesh.iam.domain.user.IamUser;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 表示仅以哈希形式保存的用户刷新令牌。
 *
 * <p>令牌支持一次性轮换：旧令牌被撤销并关联其替代令牌。持久层的唯一约束确保一个
 * 新令牌不能作为多个旧令牌的替代对象。</p>
 */
@Entity
@Table(name = "iam_refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private IamUser user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 255)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replaced_by_token_id")
    private RefreshToken replacedByToken;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * 供 JPA 重建实体状态使用。
     */
    protected RefreshToken() {
    }

    /**
     * 创建一个尚未持久化且未撤销的刷新令牌。
     *
     * @param user 令牌所属用户
     * @param tokenHash 原始令牌的单向哈希
     * @param expiresAt 令牌失效时间
     */
    public RefreshToken(IamUser user, String tokenHash, Instant expiresAt) {
        this.user = Objects.requireNonNull(user);
        this.tokenHash = Objects.requireNonNull(tokenHash);
        this.expiresAt = Objects.requireNonNull(expiresAt);
    }

    /**
     * 获取令牌唯一标识。
     *
     * @return 令牌标识
     */
    public UUID getId() {
        return id;
    }

    /**
     * 获取令牌所属用户。
     *
     * @return 用户实体
     */
    public IamUser getUser() {
        return user;
    }

    /**
     * 获取原始令牌的哈希值。
     *
     * @return 令牌哈希
     */
    public String getTokenHash() {
        return tokenHash;
    }

    /**
     * 获取令牌失效时间。
     *
     * @return 失效时间
     */
    public Instant getExpiresAt() {
        return expiresAt;
    }

    /**
     * 获取令牌撤销时间。
     *
     * @return 撤销时间；未撤销时为 {@code null}
     */
    public Instant getRevokedAt() {
        return revokedAt;
    }

    /**
     * 获取替代当前令牌的新令牌。
     *
     * @return 替代令牌；未轮换时为 {@code null}
     */
    public RefreshToken getReplacedByToken() {
        return replacedByToken;
    }

    /**
     * 获取 JPA 乐观锁版本。
     *
     * @return 实体版本号
     */
    public Long getVersion() {
        return version;
    }

    /**
     * 获取令牌创建时间。
     *
     * @return 创建时间
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * 判断令牌在指定时间是否仍可用于轮换。
     *
     * @param occurredAt 待判断时间
     * @return 未撤销且尚未过期时为 {@code true}
     */
    public boolean isActiveAt(Instant occurredAt) {
        Instant instant = Objects.requireNonNull(occurredAt);
        return revokedAt == null && expiresAt.isAfter(instant);
    }

    /**
     * 撤销令牌。重复调用不会覆盖首次撤销时间。
     *
     * @param occurredAt 撤销发生时间
     */
    public void revoke(Instant occurredAt) {
        if (revokedAt == null) {
            revokedAt = Objects.requireNonNull(occurredAt);
        }
    }

    /**
     * 将当前令牌标记为已由新令牌替代，并撤销当前令牌。
     *
     * @param replacement 已持久化的替代令牌
     * @param occurredAt 轮换发生时间
     */
    public void replaceWith(RefreshToken replacement, Instant occurredAt) {
        this.replacedByToken = Objects.requireNonNull(replacement);
        revoke(occurredAt);
    }

    @PrePersist
    private void onCreate() {
        this.createdAt = Instant.now();
    }
}

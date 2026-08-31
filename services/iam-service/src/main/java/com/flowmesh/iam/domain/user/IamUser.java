package com.flowmesh.iam.domain.user;

import com.flowmesh.iam.domain.tenant.Tenant;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * 表示归属于一个租户的 IAM 用户。
 *
 * <p>用户名在租户内唯一，并在写入前归一化为小写。密码字段仅保存经过密码编码器处理的哈希值。</p>
 */
public class IamUser {

    private UUID id;

    private Tenant tenant;

    private String username;

    private String passwordHash;

    private String displayName;

    private UserStatus status;

    private Instant lastLoginAt;

    private Long version;

    private Instant createdAt;

    private Instant updatedAt;

    /**
     * 供 MyBatis 重建持久化对象状态使用。
     */
    protected IamUser() {
    }

    /**
     * 创建一个状态为 {@link UserStatus#ACTIVE} 的用户。
     *
     * @param tenant 用户所属租户
     * @param username 登录用户名
     * @param passwordHash 已编码的密码哈希
     * @param displayName 用户展示名称
     */
    public IamUser(
        Tenant tenant,
        String username,
        String passwordHash,
        String displayName
    ) {
        Instant now = Instant.now();
        this.id = UUID.randomUUID();
        this.tenant = Objects.requireNonNull(tenant);
        this.username = normalizeUsername(username);
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.displayName = Objects.requireNonNull(displayName);
        this.status = UserStatus.ACTIVE;
        this.version = 0L;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * 获取用户唯一标识。
     *
     * @return 用户标识
     */
    public UUID getId() {
        return id;
    }

    /**
     * 获取用户所属租户。
     *
     * @return 所属租户
     */
    public Tenant getTenant() {
        return tenant;
    }

    /**
     * 获取已归一化的登录用户名。
     *
     * @return 小写用户名
     */
    public String getUsername() {
        return username;
    }

    /**
     * 获取密码哈希。
     *
     * @return 已编码的密码哈希
     */
    public String getPasswordHash() {
        return passwordHash;
    }

    /**
     * 获取用户展示名称。
     *
     * @return 展示名称
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 获取用户当前状态。
     *
     * @return 用户状态
     */
    public UserStatus getStatus() {
        return status;
    }

    /**
     * 获取最近一次成功登录时间。
     *
     * @return 最近登录时间；从未登录时为 {@code null}
     */
    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    /**
     * 获取 MyBatis 条件更新使用的版本号。
     *
     * @return 实体版本号
     */
    public Long getVersion() {
        return version;
    }

    /**
     * 获取用户创建时间。
     *
     * @return 创建时间
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * 获取最近一次持久化更新时间。
     *
     * @return 更新时间
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * 更新已完成编码的密码哈希。
     *
     * @param passwordHash 新密码哈希
     */
    public void changePasswordHash(String passwordHash) {
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.updatedAt = Instant.now();
    }

    /**
     * 记录一次已完成身份校验的成功登录。
     *
     * @param occurredAt 登录成功时间
     */
    public void recordSuccessfulLogin(Instant occurredAt) {
        this.lastLoginAt = Objects.requireNonNull(occurredAt);
        this.updatedAt = occurredAt;
    }

    /**
     * 停用用户，阻断后续认证。
     */
    public void disable() {
        this.status = UserStatus.DISABLED;
        this.updatedAt = Instant.now();
    }

    /**
     * 锁定用户，供异常登录或人工处置场景使用。
     */
    public void lock() {
        this.status = UserStatus.LOCKED;
        this.updatedAt = Instant.now();
    }

    /**
     * 将用户恢复为可认证状态。
     */
    public void activate() {
        this.status = UserStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    /**
     * 归一化登录用户名，确保租户内唯一索引不受大小写差异影响。
     *
     * @param username 原始用户名
     * @return 去除首尾空白后的全小写用户名
     */
    public static String normalizeUsername(String username) {
        return Objects.requireNonNull(username).trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof IamUser other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

}

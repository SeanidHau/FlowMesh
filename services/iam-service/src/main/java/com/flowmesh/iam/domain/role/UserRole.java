package com.flowmesh.iam.domain.role;

import com.flowmesh.iam.domain.user.IamUser;
import java.time.Instant;
import java.util.Objects;

/**
 * 表示一次用户角色授予关系。
 *
 * <p>关联表保留授予时间，因此不使用无法承载额外字段的 {@code ManyToMany} 映射。</p>
 */
public class UserRole {

    private UserRoleId id;

    private IamUser user;

    private IamRole role;

    private Instant assignedAt;

    /**
     * 供 MyBatis 重建持久化对象状态使用。
     */
    protected UserRole() {
    }

    /**
     * 为已持久化的用户授予已持久化的角色。
     *
     * @param user 被授权用户
     * @param role 被授予角色
     */
    public UserRole(IamUser user, IamRole role) {
        this.user = Objects.requireNonNull(user);
        this.role = Objects.requireNonNull(role);
        this.id = new UserRoleId(user.getId(), role.getId());
        this.assignedAt = Instant.now();
    }

    /**
     * 获取关联关系的复合主键。
     *
     * @return 用户角色复合主键
     */
    public UserRoleId getId() {
        return id;
    }

    /**
     * 获取被授权用户。
     *
     * @return 用户实体
     */
    public IamUser getUser() {
        return user;
    }

    /**
     * 获取被授予角色。
     *
     * @return 角色实体
     */
    public IamRole getRole() {
        return role;
    }

    /**
     * 获取角色授予时间。
     *
     * @return 授予时间
     */
    public Instant getAssignedAt() {
        return assignedAt;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof UserRole other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

}

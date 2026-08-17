package com.flowmesh.iam.domain.role;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * 表示用户与角色关联的复合主键。
 */
@Embeddable
public class UserRoleId implements Serializable {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    /**
     * 供 JPA 重建嵌入式主键使用。
     */
    protected UserRoleId() {
    }

    /**
     * 创建用户与角色的复合主键。
     *
     * @param userId 用户标识
     * @param roleId 角色标识
     */
    public UserRoleId(UUID userId, UUID roleId) {
        this.userId = Objects.requireNonNull(userId);
        this.roleId = Objects.requireNonNull(roleId);
    }

    /**
     * 获取用户标识。
     *
     * @return 用户标识
     */
    public UUID getUserId() {
        return userId;
    }

    /**
     * 获取角色标识。
     *
     * @return 角色标识
     */
    public UUID getRoleId() {
        return roleId;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof UserRoleId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId)
            && Objects.equals(roleId, that.roleId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, roleId);
    }
}

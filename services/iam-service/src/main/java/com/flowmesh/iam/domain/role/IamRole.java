package com.flowmesh.iam.domain.role;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * 表示可被授予 IAM 用户的全局角色。
 *
 * <p>角色代码在写入前统一转换为大写，作为认证令牌中角色声明的稳定标识。</p>
 */
public class IamRole {

    private UUID id;

    private String code;

    private String name;

    private String description;

    private Instant createdAt;

    /**
     * 供 MyBatis 重建持久化对象状态使用。
     */
    protected IamRole() {
    }

    /**
     * 创建一个尚未持久化的角色。
     *
     * @param code 角色代码
     * @param name 角色展示名称
     * @param description 角色职责说明；可以为空
     */
    public IamRole(String code, String name, String description) {
        this.id = UUID.randomUUID();
        this.code = normalizeCode(code);
        this.name = Objects.requireNonNull(name);
        this.description = description;
        this.createdAt = Instant.now();
    }

    /**
     * 获取角色唯一标识。
     *
     * @return 角色标识
     */
    public UUID getId() {
        return id;
    }

    /**
     * 获取已归一化的角色代码。
     *
     * @return 大写角色代码
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取角色展示名称。
     *
     * @return 角色名称
     */
    public String getName() {
        return name;
    }

    /**
     * 获取角色职责说明。
     *
     * @return 角色说明；可以为空
     */
    public String getDescription() {
        return description;
    }

    /**
     * 获取角色创建时间。
     *
     * @return 创建时间
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * 归一化角色代码，确保代码唯一性不受大小写和首尾空白影响。
     *
     * @param code 原始角色代码
     * @return 去除首尾空白后的大写角色代码
     */
    public static String normalizeCode(String code) {
        return Objects.requireNonNull(code).trim().toUpperCase(Locale.ROOT);
    }

}

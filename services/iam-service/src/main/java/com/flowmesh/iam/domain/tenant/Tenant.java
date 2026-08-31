package com.flowmesh.iam.domain.tenant;


import java.time.Instant;
import java.util.Objects;

/**
 * 表示 FlowMesh 的租户边界。
 *
 * <p>租户标识在创建后不可改变。用户与后续业务数据均通过该标识建立隔离关系。</p>
 */
public class Tenant {

    private String id;

    private String name;

    private TenantStatus status;

    private Instant createdAt;

    private Instant updatedAt;

    /**
     * 供 MyBatis 重建持久化对象状态使用。
     */
    protected Tenant() {
    }

    /**
     * 创建一个尚未持久化的租户。
     *
     * @param id 租户唯一标识
     * @param name 租户展示名称
     * @param status 初始租户状态
     */
    public Tenant(String id, String name, TenantStatus status) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.status = Objects.requireNonNull(status);
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /**
     * 获取不可变的租户标识。
     *
     * @return 租户标识
     */
    public String getId() {
        return id;
    }

    /**
     * 获取租户展示名称。
     *
     * @return 租户名称
     */
    public String getName() {
        return name;
    }

    /**
     * 获取当前租户状态。
     *
     * @return 租户状态
     */
    public TenantStatus getStatus() {
        return status;
    }

    /**
     * 获取租户创建时间。
     *
     * @return 创建时间
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * 获取最近一次持久化更新的时间。
     *
     * @return 更新时间
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * 修改租户展示名称。
     *
     * @param name 新名称
     */
    public void rename(String name) {
        this.name = Objects.requireNonNull(name);
        this.updatedAt = Instant.now();
    }

    /**
     * 启用租户，使其可继续承载业务用户和数据。
     */
    public void enable() {
        this.status = TenantStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    /**
     * 停用租户。
     */
    public void disable() {
        this.status = TenantStatus.DISABLED;
        this.updatedAt = Instant.now();
    }
}

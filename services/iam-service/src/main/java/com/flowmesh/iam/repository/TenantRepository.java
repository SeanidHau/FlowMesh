package com.flowmesh.iam.repository;

import com.flowmesh.iam.domain.tenant.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 管理租户聚合的持久化访问。
 */
public interface TenantRepository extends JpaRepository<Tenant, String> {
}

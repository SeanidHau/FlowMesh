package com.flowmesh.supplier.repository;

import com.flowmesh.supplier.domain.SupplierApplication;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 管理供应商申请的持久化访问。
 */
public interface SupplierApplicationRepository extends JpaRepository<SupplierApplication, UUID> {
}

package com.flowmesh.iam.repository;

import com.flowmesh.iam.domain.audit.AuditEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 持久化 IAM 安全审计事件。
 */
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
}

package com.flowmesh.supplier.repository;

import com.flowmesh.supplier.domain.WorkflowEventInbox;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 管理 supplier-service 的 workflow 事件幂等记录。
 */
public interface WorkflowEventInboxRepository extends JpaRepository<WorkflowEventInbox, UUID> {
}

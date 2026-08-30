package com.flowmesh.workflow.repository;

import com.flowmesh.workflow.domain.WorkflowInstance;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 访问流程实例投影。
 */
public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstance, UUID> {

    /**
     * 判断某个领域事件是否已创建流程实例。
     *
     * @param sourceEventId 领域事件标识
     * @return 已处理时为 {@code true}
     */
    boolean existsBySourceEventId(UUID sourceEventId);
}

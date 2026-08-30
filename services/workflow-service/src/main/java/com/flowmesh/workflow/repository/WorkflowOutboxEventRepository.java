package com.flowmesh.workflow.repository;

import com.flowmesh.workflow.domain.WorkflowOutboxEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 访问 workflow-service 的待投递事件。
 */
public interface WorkflowOutboxEventRepository extends JpaRepository<WorkflowOutboxEvent, UUID> {

    /**
     * 按创建时间读取一批尚未成功投递的事件。
     *
     * @return 最早创建的待投递事件，最多 100 条
     */
    List<WorkflowOutboxEvent> findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();

    /**
     * 按申请和事件类型查询已写入的 Outbox 事件。
     *
     * @param aggregateId 申请标识
     * @param tag 事件 Tag
     * @return 匹配事件
     */
    List<WorkflowOutboxEvent> findAllByAggregateIdAndTag(UUID aggregateId, String tag);
}

package com.flowmesh.supplier.repository;

import com.flowmesh.supplier.domain.WorkflowEventInbox;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 使用 MyBatis 访问 workflow 事件 Inbox。
 */
@Mapper
public interface WorkflowEventInboxRepository {

    /**
     * 判断事件是否已经处理。
     *
     * @param eventId 事件标识
     * @return 已处理时为 {@code true}
     */
    boolean existsById(@Param("eventId") UUID eventId);

    /**
     * 统计当前 Inbox 中的事件数量。
     *
     * @return 事件数量
     */
    long count();

    /**
     * 统计指定申请的审批完成事件数量。
     *
     * @param aggregateId 申请标识
     * @return 事件数量
     */
    long countByAggregateId(@Param("aggregateId") UUID aggregateId);

    /**
     * 保存事件 Inbox 记录。
     *
     * @param inbox Inbox 记录
     * @return 受影响行数
     */
    int save(WorkflowEventInbox inbox);
}

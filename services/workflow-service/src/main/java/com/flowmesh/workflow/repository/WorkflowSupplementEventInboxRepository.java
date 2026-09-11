package com.flowmesh.workflow.repository;

import com.flowmesh.workflow.domain.WorkflowSupplementEventInbox;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 使用 MyBatis 访问补件提交事件 Inbox。
 */
@Mapper
public interface WorkflowSupplementEventInboxRepository {

    /**
     * 判断事件是否已经处理。
     *
     * @param eventId 事件标识
     * @return 已处理时为 {@code true}
     */
    boolean existsById(@Param("eventId") UUID eventId);

    /**
     * 保存事件 Inbox。
     *
     * @param inbox 幂等记录
     * @return 受影响行数
     */
    int save(WorkflowSupplementEventInbox inbox);
}

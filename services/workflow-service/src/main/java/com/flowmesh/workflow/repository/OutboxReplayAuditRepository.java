package com.flowmesh.workflow.repository;

import com.flowmesh.workflow.domain.OutboxReplayAudit;
import org.apache.ibatis.annotations.Mapper;

/**
 * 使用 MyBatis 保存 workflow Outbox 重放审计。
 */
@Mapper
public interface OutboxReplayAuditRepository {

    /**
     * 保存一条重放审计记录。
     *
     * @param audit 重放审计
     * @return 受影响行数
     */
    int insert(OutboxReplayAudit audit);
}

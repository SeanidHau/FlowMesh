package com.flowmesh.iam.repository;

import com.flowmesh.iam.domain.audit.AuditEvent;
import org.apache.ibatis.annotations.Mapper;

/**
 * 使用 MyBatis 追加 IAM 安全审计事件。
 */
@Mapper
public interface AuditEventRepository {

    /**
     * 保存审计事件。
     *
     * @param event 审计事件
     * @return 受影响行数
     */
    int save(AuditEvent event);
}

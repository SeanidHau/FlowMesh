package com.flowmesh.workflow.repository;

import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 风控结果事件幂等记录仓储。
 */
@Mapper
public interface RiskEventInboxRepository {

    /**
     * 判断风控结果事件是否已经处理。
     *
     * @param eventId 事件标识
     * @return 已处理时为 true
     */
    boolean existsById(@Param("eventId") UUID eventId);

    /**
     * 保存风控结果事件幂等记录。
     *
     * @param eventId 事件标识
     * @param tenantId 租户标识
     * @param applicationId 申请标识
     * @return 受影响行数
     */
    int insert(
        @Param("eventId") UUID eventId,
        @Param("tenantId") String tenantId,
        @Param("applicationId") UUID applicationId
    );
}

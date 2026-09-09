package com.flowmesh.notificationaudit.repository;

import java.time.Instant;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 审计事件写入仓储。
 */
@Mapper
public interface AuditEventRepository {

    /**
     * 判断事件是否已经处理。
     *
     * @param eventId 事件标识
     * @return 是否已经处理
     */
    boolean existsByEventId(@Param("eventId") UUID eventId);

    /**
     * 写入不可变审计事件。
     *
     * @return 受影响行数
     */
    int insert(
        @Param("id") UUID id,
        @Param("eventId") UUID eventId,
        @Param("tenantId") String tenantId,
        @Param("aggregateId") UUID aggregateId,
        @Param("eventType") String eventType,
        @Param("traceId") String traceId,
        @Param("payload") String payload,
        @Param("occurredAt") Instant occurredAt
    );

    /**
     * 记录事件消费完成，用于持久化幂等。
     *
     * @return 受影响行数
     */
    int insertInbox(
        @Param("eventId") UUID eventId,
        @Param("tenantId") String tenantId,
        @Param("aggregateId") UUID aggregateId
    );
}

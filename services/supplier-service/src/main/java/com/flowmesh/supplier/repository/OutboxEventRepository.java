package com.flowmesh.supplier.repository;

import com.flowmesh.supplier.domain.OutboxEvent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 访问 supplier 服务的 Outbox 事件。
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * 按创建时间读取一批尚未成功投递的事件。
     *
     * @return 最早创建的待投递事件，最多 100 条
     */
    List<OutboxEvent> findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();

    /**
     * 按业务聚合读取其 Outbox 事件。
     *
     * @param aggregateId 业务聚合标识
     * @return 对应事件
     */
    Optional<OutboxEvent> findByAggregateIdAndTag(UUID aggregateId, String tag);
}

package com.flowmesh.supplier.repository;

import com.flowmesh.supplier.domain.IdempotencyRecord;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 管理幂等记录的持久化访问。
 */
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

    /**
     * 按租户、用户和幂等键查询记录。
     *
     * @param tenantId 租户标识
     * @param userId 用户标识
     * @param idempotencyKey 幂等键
     * @return 匹配的幂等记录；不存在时为空
     */
    Optional<IdempotencyRecord> findByTenantIdAndUserIdAndIdempotencyKey(
        String tenantId,
        UUID userId,
        String idempotencyKey
    );
}

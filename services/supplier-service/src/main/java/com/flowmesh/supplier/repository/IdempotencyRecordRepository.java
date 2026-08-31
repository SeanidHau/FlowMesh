package com.flowmesh.supplier.repository;

import com.flowmesh.supplier.domain.IdempotencyRecord;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 使用 MyBatis 访问供应商接口幂等记录。
 */
@Mapper
public interface IdempotencyRecordRepository {

    /**
     * 按租户、用户和幂等键查询记录。
     *
     * @param tenantId 租户标识
     * @param userId 用户标识
     * @param idempotencyKey 幂等键
     * @return 幂等记录；不存在时为空
     */
    Optional<IdempotencyRecord> findByTenantIdAndUserIdAndIdempotencyKey(
        @Param("tenantId") String tenantId,
        @Param("userId") UUID userId,
        @Param("idempotencyKey") String idempotencyKey
    );

    /**
     * 插入幂等记录。
     *
     * @param record 待保存记录
     * @return 保存后的记录对象
     */
    default IdempotencyRecord saveAndFlush(IdempotencyRecord record) {
        insert(record);
        return record;
    }

    /**
     * 插入幂等记录。
     *
     * @param record 待保存记录
     * @return 受影响行数
     */
    int insert(IdempotencyRecord record);
}

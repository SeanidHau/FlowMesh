package com.flowmesh.supplier.application;

import com.flowmesh.supplier.domain.IdempotencyRecord;
import com.flowmesh.supplier.repository.IdempotencyRecordRepository;
import com.flowmesh.supplier.rls.TenantRlsInitializer;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 在独立只读事务中回放幂等记录。
 *
 * <p>并发首请求发生唯一键冲突时，创建事务已经回滚，必须在新事务中读取
 * 获胜请求写入的响应快照。</p>
 */
@Service
public class IdempotencyReplayService {

    private final IdempotencyRecordRepository repository;
    private final TenantRlsInitializer tenantRlsInitializer;

    /**
     * 创建幂等回放服务。
     *
     * @param repository 幂等记录仓储
     */
    public IdempotencyReplayService(
        IdempotencyRecordRepository repository,
        TenantRlsInitializer tenantRlsInitializer
    ) {
        this.repository = repository;
        this.tenantRlsInitializer = tenantRlsInitializer;
    }

    /**
     * 按请求指纹回放首个响应。
     *
     * @param tenantId 租户标识
     * @param userId 用户标识
     * @param key 幂等键
     * @param fingerprint 请求指纹
     * @return 首个响应；并发事务尚未可见时为空
     * @throws IdempotencyKeyConflictException 同键对应不同请求体
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<SupplierApplicationService.CreateResult> replay(
        String tenantId,
        java.util.UUID userId,
        String key,
        String fingerprint
    ) {
        tenantRlsInitializer.initializeTenant(tenantId);
        return repository.findByTenantIdAndUserIdAndIdempotencyKey(tenantId, userId, key)
            .map(record -> toResult(record, fingerprint));
    }

    private SupplierApplicationService.CreateResult toResult(IdempotencyRecord record, String fingerprint) {
        if (!record.getRequestFingerprint().equals(fingerprint)) {
            throw new IdempotencyKeyConflictException();
        }
        return new SupplierApplicationService.CreateResult(record.getResponseStatus(), record.getResponseBody());
    }
}

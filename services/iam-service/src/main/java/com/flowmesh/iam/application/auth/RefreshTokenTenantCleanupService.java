package com.flowmesh.iam.application.auth;

import com.flowmesh.iam.repository.RefreshTokenRepository;
import com.flowmesh.iam.rls.TenantRlsInitializer;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 在独立事务中清理单个租户的失效 Refresh Token。
 *
 * <p>IAM 的业务连接强制启用 RLS，因此跨租户清理必须逐租户建立上下文；每个租户使用独立
 * 事务，避免一个租户的锁等待或失败阻塞其他租户的清理。</p>
 */
@Service
public class RefreshTokenTenantCleanupService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final TenantRlsInitializer tenantRlsInitializer;

    /**
     * 创建单租户清理服务。
     *
     * @param refreshTokenRepository Refresh Token 仓储
     * @param tenantRlsInitializer 租户上下文初始化器
     */
    public RefreshTokenTenantCleanupService(
        RefreshTokenRepository refreshTokenRepository,
        TenantRlsInitializer tenantRlsInitializer
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.tenantRlsInitializer = tenantRlsInitializer;
    }

    /**
     * 清理指定租户的一批失效令牌。
     *
     * @param tenantId 租户标识
     * @param cutoff 失效记录保留截止时间
     * @param batchSize 单次清理上限
     * @return 实际删除数量
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int cleanupTenant(String tenantId, Instant cutoff, int batchSize) {
        tenantRlsInitializer.initialize(tenantId);
        return refreshTokenRepository.deleteExpiredOrRevokedBefore(cutoff, batchSize);
    }
}

package com.flowmesh.iam.application.auth;

import com.flowmesh.iam.repository.TenantRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定期清理已经失效的 Refresh Token，避免认证数据库无限增长。
 *
 * <p>任务只删除已经过期，或已经撤销并超过保留窗口的记录；每个租户在独立事务中执行一批清理，
 * 避免强制 RLS 下跨租户操作，同时减少单次任务长时间占用数据库锁和连接；多个 IAM 副本可以并行运行，
 * 数据库会跳过彼此已锁定的候选记录。保留窗口用于保留近期安全审计所需的令牌状态。</p>
 */
@Component
@ConditionalOnProperty(
    name = "flowmesh.security.refresh-token-cleanup.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class RefreshTokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupJob.class);

    private final TenantRepository tenantRepository;
    private final RefreshTokenTenantCleanupService tenantCleanupService;
    private final Duration retention;
    private final int batchSize;
    private final Counter deletedCounter;

    /**
     * 创建 Refresh Token 清理任务。
     *
     * @param tenantRepository 租户仓储
     * @param tenantCleanupService 单租户清理服务
     * @param retention 失效记录保留时长
     * @param batchSize 单次清理上限
     * @param meterRegistry 指标注册器
     */
    public RefreshTokenCleanupJob(
        TenantRepository tenantRepository,
        RefreshTokenTenantCleanupService tenantCleanupService,
        @Value("${flowmesh.security.refresh-token-cleanup.retention:P30D}") Duration retention,
        @Value("${flowmesh.security.refresh-token-cleanup.batch-size:1000}") int batchSize,
        MeterRegistry meterRegistry
    ) {
        if (retention.isNegative() || retention.isZero()) {
            throw new IllegalArgumentException("Refresh Token cleanup retention must be positive");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Refresh Token cleanup batch size must be positive");
        }
        this.tenantRepository = tenantRepository;
        this.tenantCleanupService = tenantCleanupService;
        this.retention = retention;
        this.batchSize = batchSize;
        this.deletedCounter = Counter.builder("flowmesh.iam.refresh_token.cleanup")
            .description("Number of expired or revoked refresh tokens deleted")
            .register(meterRegistry);
    }

    /**
     * 执行一批令牌清理。
     */
    @Scheduled(
        fixedDelayString = "${flowmesh.security.refresh-token-cleanup.interval-ms:3600000}",
        initialDelayString = "${flowmesh.security.refresh-token-cleanup.initial-delay-ms:60000}"
    )
    public void cleanup() {
        Instant cutoff = Instant.now().minus(retention);
        int deleted = 0;
        RuntimeException failure = null;
        for (String tenantId : tenantRepository.findAllIds()) {
            try {
                deleted += tenantCleanupService.cleanupTenant(tenantId, cutoff, batchSize);
            } catch (RuntimeException exception) {
                log.error("Refresh Token 清理失败，tenantId={}", tenantId, exception);
                failure = exception;
            }
        }
        if (deleted > 0) {
            deletedCounter.increment(deleted);
            log.info("Refresh Token 清理完成，删除 {} 条失效记录", deleted);
        }
        if (failure != null) {
            throw failure;
        }
    }
}

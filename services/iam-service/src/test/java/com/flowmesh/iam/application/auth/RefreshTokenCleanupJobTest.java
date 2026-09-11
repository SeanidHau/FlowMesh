package com.flowmesh.iam.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowmesh.iam.repository.RefreshTokenRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 验证 Refresh Token 清理任务的批量参数、截止时间和指标行为。
 */
class RefreshTokenCleanupJobTest {

    /**
     * 验证任务按配置删除一批失效令牌，并记录删除数量。
     */
    @Test
    void shouldDeleteOneBatchAndRecordMetric() {
        RefreshTokenRepository repository = org.mockito.Mockito.mock(RefreshTokenRepository.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        when(repository.deleteExpiredOrRevokedBefore(any(Instant.class), eq(50))).thenReturn(3);
        RefreshTokenCleanupJob job = new RefreshTokenCleanupJob(
            repository, Duration.ofDays(30), 50, meterRegistry
        );

        Instant before = Instant.now().minus(Duration.ofDays(30));
        job.cleanup();
        Instant after = Instant.now().minus(Duration.ofDays(30));

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(repository).deleteExpiredOrRevokedBefore(cutoff.capture(), eq(50));
        assertThat(cutoff.getValue()).isBetween(before, after);
        assertThat(meterRegistry.get("flowmesh.iam.refresh_token.cleanup").counter().count())
            .isEqualTo(3);
    }
}

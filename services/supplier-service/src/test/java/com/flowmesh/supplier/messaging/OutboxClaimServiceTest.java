package com.flowmesh.supplier.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.flowmesh.supplier.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;

/**
 * 验证 supplier Outbox 认领参数不会产生过短租约。
 */
class OutboxClaimServiceTest {

    /**
     * 租约不足以覆盖批量发送窗口时必须拒绝启动。
     */
    @Test
    void shouldRejectLeaseShorterThanBatchSendWindow() {
        assertThatThrownBy(() -> new OutboxClaimService(
            mock(OutboxEventRepository.class), 10, 30, 3000
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("lease-seconds");
    }
}

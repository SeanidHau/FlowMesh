package com.flowmesh.notificationaudit.messaging;

import com.flowmesh.notificationaudit.config.NotificationDeliveryProperties;
import com.flowmesh.notificationaudit.domain.NotificationDelivery;
import com.flowmesh.notificationaudit.repository.NotificationDeliveryRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 在短事务中认领通知投递记录，网络请求在事务外执行。
 */
@Service
@ConditionalOnProperty(name = "flowmesh.notification.delivery.enabled", havingValue = "true")
public class NotificationDeliveryClaimService {

    private final NotificationDeliveryRepository repository;
    private final NotificationDeliveryProperties properties;

    /**
     * 创建通知投递认领服务。
     *
     * @param repository 投递仓储
     * @param properties 投递配置
     */
    public NotificationDeliveryClaimService(
        NotificationDeliveryRepository repository,
        NotificationDeliveryProperties properties
    ) {
        this.repository = repository;
        this.properties = properties;
    }

    /**
     * 认领一批可发送记录。
     *
     * @return 已认领记录
     */
    @Transactional
    public List<NotificationDelivery> claimBatch() {
        Instant now = Instant.now();
        return repository.claimBatch(
            now,
            UUID.randomUUID(),
            now.plusSeconds(properties.getLeaseSeconds()),
            properties.getBatchSize()
        );
    }
}

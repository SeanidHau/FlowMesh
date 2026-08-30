package com.flowmesh.supplier.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmesh.common.security.AuthPrincipal;
import com.flowmesh.supplier.api.dto.ApplicationResponse;
import com.flowmesh.supplier.api.dto.CreateApplicationRequest;
import com.flowmesh.supplier.domain.IdempotencyRecord;
import com.flowmesh.supplier.domain.OutboxEvent;
import com.flowmesh.supplier.domain.SupplierApplication;
import com.flowmesh.supplier.repository.IdempotencyRecordRepository;
import com.flowmesh.supplier.repository.OutboxEventRepository;
import com.flowmesh.supplier.repository.SupplierApplicationRepository;
import com.flowmesh.supplier.rls.TenantRlsInitializer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 处理供应商申请创建与请求幂等。
 *
 * <p>幂等记录与申请在同一事务内写入。重复请求（同键同指纹）回放首次响应快照，
 * 不产生第二条申请；同键不同指纹抛出 409 冲突。</p>
 */
@Service
public class SupplierApplicationService {

    private final SupplierApplicationRepository applicationRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final TenantRlsInitializer tenantRlsInitializer;
    private final ObjectMapper objectMapper;

    /**
     * 创建申请服务。
     *
     * @param applicationRepository 申请仓储
     * @param idempotencyRecordRepository 幂等记录仓储
     * @param outboxEventRepository Outbox 事件仓储
     * @param tenantRlsInitializer 租户 RLS 初始化器
     * @param objectMapper JSON 序列化器
     */
    public SupplierApplicationService(
        SupplierApplicationRepository applicationRepository,
        IdempotencyRecordRepository idempotencyRecordRepository,
        OutboxEventRepository outboxEventRepository,
        TenantRlsInitializer tenantRlsInitializer,
        ObjectMapper objectMapper
    ) {
        this.applicationRepository = applicationRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.tenantRlsInitializer = tenantRlsInitializer;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建供应商申请，支持 Idempotency-Key 幂等。
     *
     * @param principal 已认证主体
     * @param idempotencyKey 幂等键
     * @param request 创建请求
     * @param traceId 请求追踪标识
     * @return 创建结果（包含首次响应快照）
     * @throws IdempotencyKeyConflictException 同键不同请求体冲突
     */
    @Transactional
    public CreateResult create(
        AuthPrincipal principal,
        String idempotencyKey,
        CreateApplicationRequest request,
        String traceId
    ) {
        tenantRlsInitializer.initializeTenant();

        String requestBody = request.supplierName();
        String fingerprint = sha256Hex(requestBody);

        var existing = idempotencyRecordRepository
            .findByTenantIdAndUserIdAndIdempotencyKey(principal.tenantId(), principal.userId(), idempotencyKey);

        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            if (!record.getRequestFingerprint().equals(fingerprint)) {
                throw new IdempotencyKeyConflictException();
            }
            return new CreateResult(record.getResponseStatus(), record.getResponseBody());
        }

        SupplierApplication application = applicationRepository.saveAndFlush(
            new SupplierApplication(principal.tenantId(), principal.userId(), request.supplierName())
        );

        ApplicationResponse response = new ApplicationResponse(
            application.getId(),
            application.getSupplierName(),
            application.getStatus().name(),
            application.getStateVersion()
        );

        String responseJson = writeJson(response);
        int responseStatus = HttpStatus.CREATED.value();
        Instant occurredAt = Instant.now();
        UUID eventId = UUID.randomUUID();

        idempotencyRecordRepository.saveAndFlush(
            new IdempotencyRecord(
                principal.tenantId(),
                principal.userId(),
                idempotencyKey,
                fingerprint,
                responseStatus,
                responseJson
            )
        );

        outboxEventRepository.save(new OutboxEvent(
            eventId,
            principal.tenantId(),
            application.getId(),
            "supplier-events",
            "ApplicationSubmitted",
            writeJson(new ApplicationSubmittedMessage(
                eventId,
                "ApplicationSubmitted",
                1,
                principal.tenantId(),
                application.getId(),
                occurredAt,
                traceId,
                new SubmittedPayload(
                    application.getId(),
                    application.getSupplierName(),
                    principal.userId()
                )
            ))
        ));

        return new CreateResult(responseStatus, responseJson);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("响应序列化失败", e);
        }
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /**
     * Outbox 中保存的供应商申请提交事件信封。
     *
     * @param eventId 事件唯一标识
     * @param eventType 事件类型
     * @param schemaVersion 事件结构版本
     * @param tenantId 租户标识
     * @param aggregateId 申请聚合标识
     * @param occurredAt 事件发生时间
     * @param traceId 链路追踪标识
     * @param payload 事件载荷
     */
    private record ApplicationSubmittedMessage(
        UUID eventId,
        String eventType,
        int schemaVersion,
        String tenantId,
        UUID aggregateId,
        Instant occurredAt,
        String traceId,
        SubmittedPayload payload
    ) {
    }

    /**
     * 供应商申请提交事件载荷。
     *
     * @param applicationId 申请标识
     * @param supplierName 供应商名称
     * @param applicantUserId 申请人标识
     */
    private record SubmittedPayload(
        UUID applicationId,
        String supplierName,
        UUID applicantUserId
    ) {
    }

    /**
     * 创建结果，包含首次响应的状态码和 JSON 快照。
     *
     * @param status HTTP 状态码
     * @param body JSON 响应体
     */
    public record CreateResult(int status, String body) {
    }
}

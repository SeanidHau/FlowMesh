package com.flowmesh.supplier.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmesh.common.security.AuthPrincipal;
import com.flowmesh.supplier.api.dto.ApplicationResponse;
import com.flowmesh.supplier.api.dto.CreateApplicationRequest;
import com.flowmesh.supplier.domain.IdempotencyRecord;
import com.flowmesh.supplier.domain.SupplierApplication;
import com.flowmesh.supplier.repository.IdempotencyRecordRepository;
import com.flowmesh.supplier.repository.SupplierApplicationRepository;
import com.flowmesh.supplier.rls.TenantRlsInitializer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
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
    private final TenantRlsInitializer tenantRlsInitializer;
    private final ObjectMapper objectMapper;

    /**
     * 创建申请服务。
     *
     * @param applicationRepository 申请仓储
     * @param idempotencyRecordRepository 幂等记录仓储
     * @param tenantRlsInitializer 租户 RLS 初始化器
     * @param objectMapper JSON 序列化器
     */
    public SupplierApplicationService(
        SupplierApplicationRepository applicationRepository,
        IdempotencyRecordRepository idempotencyRecordRepository,
        TenantRlsInitializer tenantRlsInitializer,
        ObjectMapper objectMapper
    ) {
        this.applicationRepository = applicationRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.tenantRlsInitializer = tenantRlsInitializer;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建供应商申请，支持 Idempotency-Key 幂等。
     *
     * @param principal 已认证主体
     * @param idempotencyKey 幂等键
     * @param request 创建请求
     * @return 创建结果（包含首次响应快照）
     * @throws IdempotencyKeyConflictException 同键不同请求体冲突
     */
    @Transactional
    public CreateResult create(AuthPrincipal principal, String idempotencyKey, CreateApplicationRequest request) {
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
     * 创建结果，包含首次响应的状态码和 JSON 快照。
     *
     * @param status HTTP 状态码
     * @param body JSON 响应体
     */
    public record CreateResult(int status, String body) {
    }
}

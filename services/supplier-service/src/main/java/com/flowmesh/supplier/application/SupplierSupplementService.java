package com.flowmesh.supplier.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmesh.common.messaging.EventEnvelopeValidator;
import com.flowmesh.common.security.AuthPrincipal;
import com.flowmesh.supplier.api.dto.ApplicationResponse;
import com.flowmesh.supplier.api.dto.SupplementSubmissionRequest;
import com.flowmesh.supplier.domain.IdempotencyRecord;
import com.flowmesh.supplier.domain.OutboxEvent;
import com.flowmesh.supplier.domain.SupplierApplication;
import com.flowmesh.supplier.domain.SupplierSupplement;
import com.flowmesh.supplier.domain.WorkflowEventInbox;
import com.flowmesh.supplier.repository.IdempotencyRecordRepository;
import com.flowmesh.supplier.repository.OutboxEventRepository;
import com.flowmesh.supplier.repository.SupplierApplicationRepository;
import com.flowmesh.supplier.repository.SupplierSupplementRepository;
import com.flowmesh.supplier.repository.WorkflowEventInboxRepository;
import com.flowmesh.supplier.rls.TenantRlsInitializer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 处理补件请求和补件提交。
 *
 * <p>补件历史、申请状态、幂等响应和 {@code SupplementSubmitted} Outbox 在同一事务内提交，
 * 以保证申请人重试时不会重复开启审批轮次。</p>
 */
@Service
public class SupplierSupplementService {

    private final SupplierApplicationRepository applicationRepository;
    private final SupplierSupplementRepository supplementRepository;
    private final WorkflowEventInboxRepository inboxRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final OutboxEventRepository outboxRepository;
    private final TenantRlsInitializer tenantRlsInitializer;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final IdempotencyReplayService idempotencyReplayService;

    /**
     * 创建补件服务。
     *
     * @param applicationRepository 申请仓储
     * @param supplementRepository 补件历史仓储
     * @param inboxRepository workflow 事件 Inbox
     * @param idempotencyRecordRepository 幂等仓储
     * @param outboxRepository Outbox 仓储
     * @param tenantRlsInitializer 租户 RLS 初始化器
     * @param objectMapper JSON 序列化器
     * @param transactionManager 事务管理器
     * @param idempotencyReplayService 幂等回放服务
     */
    public SupplierSupplementService(
        SupplierApplicationRepository applicationRepository,
        SupplierSupplementRepository supplementRepository,
        WorkflowEventInboxRepository inboxRepository,
        IdempotencyRecordRepository idempotencyRecordRepository,
        OutboxEventRepository outboxRepository,
        TenantRlsInitializer tenantRlsInitializer,
        ObjectMapper objectMapper,
        org.springframework.transaction.PlatformTransactionManager transactionManager,
        IdempotencyReplayService idempotencyReplayService
    ) {
        this.applicationRepository = applicationRepository;
        this.supplementRepository = supplementRepository;
        this.inboxRepository = inboxRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.outboxRepository = outboxRepository;
        this.tenantRlsInitializer = tenantRlsInitializer;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.idempotencyReplayService = idempotencyReplayService;
    }

    /**
     * 处理 workflow 发来的补件请求，重复事件只更新一次申请状态。
     *
     * @param message RocketMQ 事件信封
     */
    @Transactional
    public void handleSupplementRequested(String message) {
        JsonNode event = readEvent(message, "SupplementRequested");
        UUID eventId = EventEnvelopeValidator.requiredUuid(event, "eventId");
        UUID applicationId = EventEnvelopeValidator.requiredUuid(event, "aggregateId");
        String tenantId = EventEnvelopeValidator.requiredText(event, "tenantId");
        JsonNode payload = EventEnvelopeValidator.validate(event, "SupplementRequested");
        EventEnvelopeValidator.requiredText(payload, "taskKey");
        EventEnvelopeValidator.requiredText(payload, "comment");
        tenantRlsInitializer.initializeTenant(tenantId);
        if (inboxRepository.existsById(eventId)) {
            return;
        }
        SupplierApplication application = applicationRepository.findById(applicationId)
            .orElseThrow(SupplierApplicationNotFoundException::new);
        application.requestSupplement();
        if (applicationRepository.updateState(application) != 1) {
            throw new org.springframework.dao.OptimisticLockingFailureException("申请状态已被其他事务更新");
        }
        inboxRepository.save(new WorkflowEventInbox(eventId, tenantId, applicationId));
    }

    /**
     * 提交补件并通过 Outbox 通知 workflow 开启下一轮审批。
     *
     * @param principal 当前认证主体
     * @param applicationId 申请标识
     * @param idempotencyKey 幂等键
     * @param request 补件请求
     * @param traceId 链路标识
     * @return 首次响应或幂等回放响应
     */
    public SupplierApplicationService.CreateResult submit(
        AuthPrincipal principal,
        UUID applicationId,
        String idempotencyKey,
        SupplementSubmissionRequest request,
        String traceId
    ) {
        String fingerprint = sha256Hex(request.comment());
        String effectiveTraceId = traceId == null || traceId.isBlank()
            ? UUID.randomUUID().toString() : traceId;
        try {
            return transactionTemplate.execute(status -> submitInTransaction(
                principal, applicationId, idempotencyKey, request, effectiveTraceId, fingerprint
            ));
        } catch (DataIntegrityViolationException exception) {
            return idempotencyReplayService.replay(
                principal.tenantId(), principal.userId(), idempotencyKey, fingerprint
            ).orElseThrow(() -> exception);
        }
    }

    private SupplierApplicationService.CreateResult submitInTransaction(
        AuthPrincipal principal,
        UUID applicationId,
        String idempotencyKey,
        SupplementSubmissionRequest request,
        String traceId,
        String fingerprint
    ) {
        tenantRlsInitializer.initializeTenant();
        var existing = idempotencyRecordRepository.findByTenantIdAndUserIdAndIdempotencyKey(
            principal.tenantId(), principal.userId(), idempotencyKey
        );
        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            if (!record.getRequestFingerprint().equals(fingerprint)) {
                throw new IdempotencyKeyConflictException();
            }
            return new SupplierApplicationService.CreateResult(
                record.getResponseStatus(), record.getResponseBody()
            );
        }

        SupplierApplication application = applicationRepository.findById(applicationId)
            .orElseThrow(SupplierApplicationNotFoundException::new);
        if (!principal.userId().equals(application.getApplicantUserId())) {
            throw new DocumentAccessDeniedException();
        }
        int roundNo = application.getSupplementCount() + 1;
        application.submitSupplement();
        if (applicationRepository.updateState(application) != 1) {
            throw new org.springframework.dao.OptimisticLockingFailureException("申请状态已被其他事务更新");
        }
        supplementRepository.insert(new SupplierSupplement(
            principal.tenantId(), applicationId, roundNo, principal.userId(), request.comment()
        ));

        ApplicationResponse response = new ApplicationResponse(
            application.getId(), application.getSupplierName(), application.getStatus().name(),
            application.getStateVersion(), application.getSupplementCount()
        );
        String responseJson = writeJson(response);
        idempotencyRecordRepository.saveAndFlush(new IdempotencyRecord(
            principal.tenantId(), principal.userId(), idempotencyKey, fingerprint,
            HttpStatus.OK.value(), responseJson
        ));

        UUID eventId = UUID.randomUUID();
        outboxRepository.save(new OutboxEvent(
            eventId,
            principal.tenantId(),
            applicationId,
            "supplier-events",
            "SupplementSubmitted",
            writeJson(new SupplementSubmittedMessage(
                eventId, "SupplementSubmitted", 1, principal.tenantId(), applicationId,
                Instant.now(), traceId,
                new SupplementSubmittedPayload(applicationId, roundNo, request.comment())
            ))
        ));
        return new SupplierApplicationService.CreateResult(HttpStatus.OK.value(), responseJson);
    }

    private JsonNode readEvent(String message, String type) {
        try {
            JsonNode event = objectMapper.readTree(message);
            EventEnvelopeValidator.validate(event, type);
            return event;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(type + " 事件 JSON 无效", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("补件事件序列化失败", exception);
        }
    }

    private static String sha256Hex(String input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    /**
     * 补件提交事件信封。
     */
    private record SupplementSubmittedMessage(
        UUID eventId,
        String eventType,
        int schemaVersion,
        String tenantId,
        UUID aggregateId,
        Instant occurredAt,
        String traceId,
        SupplementSubmittedPayload payload
    ) {
    }

    /**
     * 补件提交事件载荷。
     */
    private record SupplementSubmittedPayload(UUID applicationId, int roundNo, String comment) {
    }
}

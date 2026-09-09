package com.flowmesh.supplier.application;

import com.flowmesh.common.security.AuthPrincipal;
import com.flowmesh.supplier.api.dto.DocumentDownloadResponse;
import com.flowmesh.supplier.api.dto.SupplierDocumentResponse;
import com.flowmesh.supplier.domain.SupplierApplication;
import com.flowmesh.supplier.domain.SupplierDocument;
import com.flowmesh.supplier.repository.SupplierApplicationRepository;
import com.flowmesh.supplier.repository.SupplierDocumentRepository;
import com.flowmesh.supplier.rls.TenantRlsInitializer;
import com.flowmesh.supplier.storage.DocumentFileValidator;
import com.flowmesh.supplier.storage.FileSafetyScanner;
import com.flowmesh.supplier.storage.FileScanResult;
import com.flowmesh.supplier.storage.ObjectStorageProperties;
import com.flowmesh.supplier.storage.ObjectStorageService;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * 处理供应商申请材料的上传、元数据查询和短期下载授权。
 *
 * <p>对象存储和数据库没有分布式事务，因此先完成安全扫描和对象写入，再在本地事务中写入元数据；
 * 元数据事务失败时执行对象删除补偿，避免产生无法追踪的孤儿对象。</p>
 */
@Service
public class SupplierDocumentService {

    private static final List<String> REVIEWER_ROLES = List.of(
        "PURCHASER", "LEGAL", "FINANCE", "OPERATIONS"
    );

    private final SupplierApplicationRepository applicationRepository;
    private final SupplierDocumentRepository documentRepository;
    private final TenantRlsInitializer tenantRlsInitializer;
    private final ObjectStorageProperties storageProperties;
    private final ObjectStorageService objectStorageService;
    private final FileSafetyScanner fileSafetyScanner;
    private final TransactionTemplate transactionTemplate;

    /**
     * 创建材料应用服务。
     *
     * @param applicationRepository 申请仓储
     * @param documentRepository 材料仓储
     * @param tenantRlsInitializer 租户 RLS 初始化器
     * @param storageProperties 对象存储配置
     * @param objectStorageService 对象存储服务
     * @param fileSafetyScanner 文件安全扫描器
     * @param transactionManager 事务管理器
     */
    public SupplierDocumentService(
        SupplierApplicationRepository applicationRepository,
        SupplierDocumentRepository documentRepository,
        TenantRlsInitializer tenantRlsInitializer,
        ObjectStorageProperties storageProperties,
        ObjectStorageService objectStorageService,
        FileSafetyScanner fileSafetyScanner,
        org.springframework.transaction.PlatformTransactionManager transactionManager
    ) {
        this.applicationRepository = applicationRepository;
        this.documentRepository = documentRepository;
        this.tenantRlsInitializer = tenantRlsInitializer;
        this.storageProperties = storageProperties;
        this.objectStorageService = objectStorageService;
        this.fileSafetyScanner = fileSafetyScanner;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 上传申请材料。
     *
     * @param principal 当前认证主体
     * @param applicationId 申请标识
     * @param file 上传文件
     * @return 材料元数据
     */
    public SupplierDocumentResponse upload(
        AuthPrincipal principal,
        UUID applicationId,
        MultipartFile file
    ) {
        SupplierApplication application = loadApplication(principal, applicationId);
        if (!principal.userId().equals(application.getApplicantUserId())) {
            throw new DocumentAccessDeniedException();
        }
        if (application.getStatus() == com.flowmesh.supplier.domain.ApplicationStatus.ENABLED) {
            throw new DocumentUploadClosedException();
        }

        DocumentFileValidator.ValidatedDocument validated = DocumentFileValidator.validate(
            file,
            storageProperties.maxFileSizeBytes()
        );
        FileScanResult scanResult = fileSafetyScanner.scan(validated.content());
        if (scanResult == FileScanResult.INFECTED) {
            throw new DocumentInfectedException();
        }

        SupplierDocument document = new SupplierDocument(
            principal.tenantId(),
            applicationId,
            principal.userId(),
            objectKey(principal.tenantId(), applicationId, validated.extension()),
            validated.originalFilename(),
            validated.contentType(),
            validated.content().length,
            validated.sha256(),
            scanResult.name()
        );

        objectStorageService.put(
            document.getObjectKey(),
            new ByteArrayInputStream(validated.content()),
            validated.content().length,
            validated.contentType()
        );
        try {
            transactionTemplate.executeWithoutResult(status -> {
                tenantRlsInitializer.initializeTenant(principal.tenantId());
                if (documentRepository.insert(document) != 1) {
                    throw new IllegalStateException("材料元数据写入失败");
                }
            });
        } catch (RuntimeException exception) {
            objectStorageService.delete(document.getObjectKey());
            throw exception;
        }
        return toResponse(document);
    }

    /**
     * 查询申请材料元数据。
     *
     * @param principal 当前认证主体
     * @param applicationId 申请标识
     * @return 材料元数据列表
     */
    public List<SupplierDocumentResponse> list(AuthPrincipal principal, UUID applicationId) {
        loadApplication(principal, applicationId);
        return transactionTemplate.execute(status -> {
            tenantRlsInitializer.initializeTenant(principal.tenantId());
            return documentRepository.findByApplicationId(applicationId).stream()
                .map(this::toResponse)
                .toList();
        });
    }

    /**
     * 创建材料短期下载 URL。
     *
     * @param principal 当前认证主体
     * @param applicationId 申请标识
     * @param documentId 材料标识
     * @return 短期下载地址
     */
    public DocumentDownloadResponse createDownloadUrl(
        AuthPrincipal principal,
        UUID applicationId,
        UUID documentId
    ) {
        loadApplication(principal, applicationId);
        SupplierDocument document = transactionTemplate.execute(status -> {
            tenantRlsInitializer.initializeTenant(principal.tenantId());
            return documentRepository.findById(documentId)
                .filter(item -> applicationId.equals(item.getApplicationId()))
                .orElseThrow(SupplierDocumentNotFoundException::new);
        });
        String url = objectStorageService.createPresignedDownloadUrl(document.getObjectKey());
        return new DocumentDownloadResponse(
            url,
            Instant.now().plus(storageProperties.presignedUrlExpirySeconds(), ChronoUnit.SECONDS)
        );
    }

    private SupplierApplication loadApplication(AuthPrincipal principal, UUID applicationId) {
        SupplierApplication application = transactionTemplate.execute(status -> {
            tenantRlsInitializer.initializeTenant(principal.tenantId());
            return applicationRepository.findById(applicationId)
                .orElseThrow(SupplierApplicationNotFoundException::new);
        });
        if (!principal.userId().equals(application.getApplicantUserId())
            && principal.roles().stream().noneMatch(REVIEWER_ROLES::contains)) {
            throw new DocumentAccessDeniedException();
        }
        return application;
    }

    private static String objectKey(String tenantId, UUID applicationId, String extension) {
        return tenantId + "/" + applicationId + "/" + UUID.randomUUID() + "." + extension;
    }

    private SupplierDocumentResponse toResponse(SupplierDocument document) {
        return new SupplierDocumentResponse(
            document.getId(),
            document.getOriginalFilename(),
            document.getContentType(),
            document.getSizeBytes(),
            document.getSha256(),
            document.getScanStatus(),
            document.getCreatedAt()
        );
    }
}

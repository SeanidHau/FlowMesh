package com.flowmesh.supplier.api;

import com.flowmesh.common.security.AuthPrincipal;
import com.flowmesh.common.security.TraceIdFilter;
import com.flowmesh.supplier.api.dto.ApplicationResponse;
import com.flowmesh.supplier.api.dto.CreateApplicationRequest;
import com.flowmesh.supplier.api.dto.DocumentDownloadResponse;
import com.flowmesh.supplier.api.dto.SupplierDocumentResponse;
import com.flowmesh.supplier.application.SupplierApplicationService;
import com.flowmesh.supplier.application.SupplierDocumentService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 供应商申请控制器。
 *
 * <p>POST /api/v1/supplier-applications 创建申请，需认证且持有 APPLICANT 角色，
 * 必须携带 Idempotency-Key 请求头。</p>
 */
@RestController
@RequestMapping("/api/v1/supplier-applications")
public class SupplierApplicationController {

    private final SupplierApplicationService applicationService;
    private final SupplierDocumentService documentService;

    /**
     * 创建申请控制器。
     *
     * @param applicationService 申请服务
     */
    public SupplierApplicationController(
        SupplierApplicationService applicationService,
        SupplierDocumentService documentService
    ) {
        this.applicationService = applicationService;
        this.documentService = documentService;
    }

    /**
     * 创建供应商申请。
     *
     * @param principal 已认证主体
     * @param idempotencyKey 幂等键
     * @param request 创建请求
     * @param httpRequest HTTP 请求
     * @return 首次响应或回放快照
     */
    @PostMapping
    public ResponseEntity<String> create(
        @AuthenticationPrincipal AuthPrincipal principal,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @Valid @RequestBody CreateApplicationRequest request,
        HttpServletRequest httpRequest
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new MissingIdempotencyKeyException();
        }
        if (idempotencyKey.length() > 128) {
            throw new InvalidIdempotencyKeyException();
        }

        var result = applicationService.create(
            principal, idempotencyKey, request, TraceIdFilter.currentTraceId(httpRequest)
        );

        return ResponseEntity
            .status(result.status())
            .contentType(MediaType.APPLICATION_JSON)
            .body(result.body());
    }

    /**
     * 查询当前租户下的供应商申请。
     *
     * @param principal 已认证主体
     * @param applicationId 申请标识
     * @return 申请当前状态
     */
    @GetMapping("/{applicationId}")
    public ApplicationResponse find(
        @AuthenticationPrincipal AuthPrincipal principal,
        @PathVariable UUID applicationId
    ) {
        return applicationService.find(principal, applicationId);
    }

    /**
     * 上传当前申请的供应商材料。
     *
     * @param principal 已认证主体
     * @param applicationId 申请标识
     * @param file Multipart 文件
     * @return 材料元数据
     */
    @PostMapping(path = "/{applicationId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SupplierDocumentResponse> uploadDocument(
        @AuthenticationPrincipal AuthPrincipal principal,
        @PathVariable UUID applicationId,
        @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.status(201).body(documentService.upload(principal, applicationId, file));
    }

    /**
     * 查询当前申请的材料元数据。
     *
     * @param principal 已认证主体
     * @param applicationId 申请标识
     * @return 材料元数据
     */
    @GetMapping("/{applicationId}/documents")
    public List<SupplierDocumentResponse> listDocuments(
        @AuthenticationPrincipal AuthPrincipal principal,
        @PathVariable UUID applicationId
    ) {
        return documentService.list(principal, applicationId);
    }

    /**
     * 为材料生成短期下载地址。
     *
     * @param principal 已认证主体
     * @param applicationId 申请标识
     * @param documentId 材料标识
     * @return 短期下载 URL
     */
    @GetMapping("/{applicationId}/documents/{documentId}/download-url")
    public DocumentDownloadResponse createDocumentDownloadUrl(
        @AuthenticationPrincipal AuthPrincipal principal,
        @PathVariable UUID applicationId,
        @PathVariable UUID documentId
    ) {
        return documentService.createDownloadUrl(principal, applicationId, documentId);
    }
}

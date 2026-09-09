package com.flowmesh.supplier.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/**
 * 验证材料上传的基础安全边界。
 */
class DocumentFileValidatorTest {

    /**
     * 合法 PDF 应通过校验并生成摘要。
     */
    @Test
    void shouldAcceptPdfWithMatchingMagicBytes() {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "../license.pdf",
            "application/pdf",
            "%PDF-1.7\ncontent".getBytes()
        );

        DocumentFileValidator.ValidatedDocument result = DocumentFileValidator.validate(file, 1024);

        assertThat(result.originalFilename()).isEqualTo("license.pdf");
        assertThat(result.extension()).isEqualTo("pdf");
        assertThat(result.sha256()).hasSize(64);
    }

    /**
     * 声明为 PDF 但文件头不匹配时必须拒绝。
     */
    @Test
    void shouldRejectMismatchedContentType() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "license.pdf", "application/pdf", "not-a-pdf".getBytes()
        );

        assertThatThrownBy(() -> DocumentFileValidator.validate(file, 1024))
            .isInstanceOf(DocumentValidationException.class)
            .hasMessage("文件内容与声明类型不匹配");
    }

    /**
     * 超过大小上限的文件必须拒绝。
     */
    @Test
    void shouldRejectOversizedFile() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "license.pdf", "application/pdf", "%PDF-1.7".getBytes()
        );

        assertThatThrownBy(() -> DocumentFileValidator.validate(file, 4))
            .isInstanceOf(DocumentValidationException.class)
            .hasMessage("上传文件超过大小限制");
    }

    /**
     * 未启用 ClamAV 时返回 SKIPPED，生产环境应通过配置开启扫描。
     */
    @Test
    void shouldSkipScannerOnlyWhenExplicitlyDisabled() {
        ClamAvFileSafetyScanner scanner = new ClamAvFileSafetyScanner(
            new FileScanProperties(false, "localhost", 3310, Duration.ofSeconds(1))
        );

        assertThat(scanner.scan(new byte[] {1, 2, 3})).isEqualTo(FileScanResult.SKIPPED);
    }
}

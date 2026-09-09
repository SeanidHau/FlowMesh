package com.flowmesh.supplier.storage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

/**
 * 对上传文件执行大小、文件名、MIME 和文件头校验，并生成内容摘要。
 */
public final class DocumentFileValidator {

    private static final Map<String, String> ALLOWED_CONTENT_TYPES = Map.of(
        "application/pdf", "pdf",
        "image/png", "png",
        "image/jpeg", "jpg",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"
    );

    private DocumentFileValidator() {
    }

    /**
     * 校验并读取上传文件。
     *
     * @param file Multipart 文件
     * @param maxSizeBytes 最大文件大小
     * @return 校验后的文件内容
     * @throws DocumentValidationException 文件不符合安全要求
     */
    public static ValidatedDocument validate(MultipartFile file, long maxSizeBytes) {
        if (file == null || file.isEmpty()) {
            throw new DocumentValidationException("上传文件不能为空");
        }
        if (file.getSize() > maxSizeBytes) {
            throw new DocumentValidationException("上传文件超过大小限制");
        }

        String contentType = file.getContentType() == null
            ? ""
            : file.getContentType().toLowerCase(java.util.Locale.ROOT);
        if (!ALLOWED_CONTENT_TYPES.containsKey(contentType)) {
            throw new DocumentValidationException("不支持的文件类型");
        }

        String filename = safeFilename(file.getOriginalFilename());
        try {
            byte[] content = file.getBytes();
            if (!matchesMagic(contentType, content)) {
                throw new DocumentValidationException("文件内容与声明类型不匹配");
            }
            return new ValidatedDocument(
                filename,
                contentType,
                ALLOWED_CONTENT_TYPES.get(contentType),
                content,
                sha256(content)
            );
        } catch (DocumentValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new DocumentValidationException("读取上传文件失败", exception);
        }
    }

    private static String safeFilename(String originalFilename) {
        String filename = originalFilename == null ? "" : originalFilename.replace('\\', '/');
        int lastSlash = filename.lastIndexOf('/');
        filename = lastSlash >= 0 ? filename.substring(lastSlash + 1) : filename;
        if (filename.isBlank() || filename.length() > 255
            || filename.chars().anyMatch(Character::isISOControl)) {
            throw new DocumentValidationException("文件名不合法");
        }
        return filename;
    }

    private static boolean matchesMagic(String contentType, byte[] content) {
        return switch (contentType) {
            case "application/pdf" -> startsWith(content, "%PDF-".getBytes(StandardCharsets.US_ASCII));
            case "image/png" -> startsWith(content, new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
            });
            case "image/jpeg" -> startsWith(content, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                startsWith(content, new byte[] {0x50, 0x4B, 0x03, 0x04});
            default -> false;
        };
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (content[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    /**
     * 已完成基础校验的文件。
     *
     * @param originalFilename 安全化后的原始文件名
     * @param contentType MIME 类型
     * @param extension 受控扩展名
     * @param content 文件内容
     * @param sha256 SHA-256 摘要
     */
    public record ValidatedDocument(
        String originalFilename,
        String contentType,
        String extension,
        byte[] content,
        String sha256
    ) {
    }
}

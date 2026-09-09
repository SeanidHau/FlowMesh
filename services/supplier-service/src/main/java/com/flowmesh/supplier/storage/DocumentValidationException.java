package com.flowmesh.supplier.storage;

/**
 * 上传材料校验失败异常。
 */
public class DocumentValidationException extends RuntimeException {

    /**
     * 创建校验异常。
     *
     * @param message 错误信息
     */
    public DocumentValidationException(String message) {
        super(message);
    }

    /**
     * 创建校验异常。
     *
     * @param message 错误信息
     * @param cause 原始异常
     */
    public DocumentValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.flowmesh.supplier.storage;

/**
 * 文件扫描引擎不可用异常。
 */
public class FileScanUnavailableException extends RuntimeException {

    /**
     * 创建扫描不可用异常。
     *
     * @param message 错误信息
     * @param cause 原始异常
     */
    public FileScanUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

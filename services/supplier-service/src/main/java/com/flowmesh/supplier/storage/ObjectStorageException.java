package com.flowmesh.supplier.storage;

/**
 * 对象存储不可用异常。
 */
public class ObjectStorageException extends RuntimeException {

    /**
     * 创建对象存储异常。
     *
     * @param message 错误信息
     * @param cause 原始异常
     */
    public ObjectStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}

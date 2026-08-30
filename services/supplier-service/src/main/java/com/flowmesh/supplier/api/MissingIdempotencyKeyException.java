package com.flowmesh.supplier.api;

/**
 * 缺少 Idempotency-Key 头异常，由全局异常处理器映射为 400。
 */
public class MissingIdempotencyKeyException extends RuntimeException {
}

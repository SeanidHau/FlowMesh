package com.flowmesh.supplier.application;

/**
 * 幂等键用途冲突异常，由全局异常处理器映射为 409。
 */
public class IdempotencyKeyConflictException extends RuntimeException {
}

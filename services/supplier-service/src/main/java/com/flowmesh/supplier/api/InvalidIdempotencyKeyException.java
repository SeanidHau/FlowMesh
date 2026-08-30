package com.flowmesh.supplier.api;

/**
 * 幂等键为空白或超过协议允许长度时抛出的异常。
 */
public class InvalidIdempotencyKeyException extends RuntimeException {
}

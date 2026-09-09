package com.flowmesh.supplier.api.dto;

import java.time.Instant;

/**
 * 材料短期下载地址响应。
 *
 * @param url 对象存储预签名 URL
 * @param expiresAt URL 过期时间
 */
public record DocumentDownloadResponse(
    String url,
    Instant expiresAt
) {
}

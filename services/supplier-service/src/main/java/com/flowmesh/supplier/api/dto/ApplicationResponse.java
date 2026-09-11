package com.flowmesh.supplier.api.dto;

import java.util.UUID;

/**
 * 供应商申请响应。
 *
 * @param id 申请标识
 * @param supplierName 供应商名称
 * @param status 申请状态
 * @param stateVersion 状态版本
 * @param supplementCount 已使用的补件次数
 */
public record ApplicationResponse(
    UUID id,
    String supplierName,
    String status,
    long stateVersion,
    int supplementCount
) {
}

package com.flowmesh.supplier.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 创建供应商申请请求。
 *
 * @param supplierName 供应商名称
 */
public record CreateApplicationRequest(
    @NotBlank String supplierName
) {
}

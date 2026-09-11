package com.flowmesh.supplier.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 申请人补件请求。
 *
 * @param comment 本轮补件说明
 */
public record SupplementSubmissionRequest(
    @NotBlank @Size(max = 2000) String comment
) {
}

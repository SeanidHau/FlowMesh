package com.flowmesh.supplier.api.dto;

import java.util.List;
import java.util.UUID;

/**
 * 供应商申请与 workflow 状态对账结果。
 *
 * @param applicationId 申请标识
 * @param consistent 是否一致
 * @param discrepancies 差异类型
 * @param caseIds 待处置记录标识
 */
public record ReconciliationResponse(
    UUID applicationId,
    boolean consistent,
    List<String> discrepancies,
    List<UUID> caseIds
) {
}

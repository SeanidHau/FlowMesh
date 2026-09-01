package com.flowmesh.workflow.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 死信重放请求。
 *
 * @param reason 重放原因，写入不可变审计记录
 */
public record ReplayOutboxRequest(
    @NotBlank @Size(max = 500) String reason
) {
}

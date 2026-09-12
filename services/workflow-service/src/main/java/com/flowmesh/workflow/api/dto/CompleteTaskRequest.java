package com.flowmesh.workflow.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 完成流程任务请求。
 *
 * @param taskKey 待完成的任务键
 * @param decision 审批决定；为空时兼容旧客户端按 APPROVE 处理
 * @param comment 审批意见；退回补件时必填
 */
public record CompleteTaskRequest(
    @NotBlank @Size(max = 64) String taskKey,
    String decision,
    @Size(max = 2000) String comment
) {
}

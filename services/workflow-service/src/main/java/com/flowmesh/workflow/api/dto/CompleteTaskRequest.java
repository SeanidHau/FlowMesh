package com.flowmesh.workflow.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 完成流程任务请求。
 *
 * @param taskKey 待完成的任务键
 */
public record CompleteTaskRequest(@NotBlank String taskKey) {
}

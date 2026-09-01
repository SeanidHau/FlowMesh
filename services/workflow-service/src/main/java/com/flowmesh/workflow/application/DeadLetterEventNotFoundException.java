package com.flowmesh.workflow.application;

/**
 * 表示当前租户下不存在可重放的死信事件。
 */
public class DeadLetterEventNotFoundException extends RuntimeException {
}

-- 作用：允许已完成的流程实例没有当前待办节点。
-- 末节点完成后，workflow_instances.current_task 按领域模型置为 NULL。
ALTER TABLE workflow_instances
    ALTER COLUMN current_task DROP NOT NULL;

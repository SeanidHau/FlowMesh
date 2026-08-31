package com.flowmesh.workflow.repository;

import com.flowmesh.workflow.domain.WorkflowInstance;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 使用 MyBatis 访问流程实例投影。
 */
@Mapper
public interface WorkflowInstanceRepository {

    /**
     * 判断领域事件是否已创建流程实例。
     *
     * @param sourceEventId 领域事件标识
     * @return 已处理时为 {@code true}
     */
    boolean existsBySourceEventId(@Param("sourceEventId") UUID sourceEventId);

    /**
     * 按申请标识查询流程实例。
     *
     * @param applicationId 申请标识
     * @return 流程实例；不存在时为空
     */
    Optional<WorkflowInstance> findByApplicationId(@Param("applicationId") UUID applicationId);

    /**
     * 插入流程实例。
     *
     * @param instance 待保存流程实例
     * @return 保存后的流程实例
     */
    default WorkflowInstance save(WorkflowInstance instance) {
        insert(instance);
        return instance;
    }

    /**
     * 按版本条件推进流程实例。
     *
     * @param instance 待更新流程实例
     * @return 受影响行数
     */
    int updateState(WorkflowInstance instance);

    /**
     * 插入流程实例记录。
     *
     * @param instance 待保存流程实例
     * @return 受影响行数
     */
    int insert(WorkflowInstance instance);
}

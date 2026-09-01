package com.flowmesh.supplier.repository;

import com.flowmesh.supplier.domain.ReconciliationCase;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 使用 MyBatis 保存供应商状态对账记录。
 */
@Mapper
public interface ReconciliationCaseRepository {

    /**
     * 新建或刷新一条打开状态的对账记录。
     *
     * @param reconciliationCase 对账记录
     * @return 受影响行数
     */
    int upsertOpen(ReconciliationCase reconciliationCase);

    /**
     * 将申请当前已不存在的差异标记为已解决。
     *
     * @param tenantId 租户标识
     * @param applicationId 申请标识
     * @return 受影响行数
     */
    int resolveOpenByApplication(
        @Param("tenantId") String tenantId,
        @Param("applicationId") UUID applicationId
    );
}

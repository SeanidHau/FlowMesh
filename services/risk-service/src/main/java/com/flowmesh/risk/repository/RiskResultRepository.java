package com.flowmesh.risk.repository;

import com.flowmesh.risk.domain.RiskResult;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 风控结果仓储。
 */
@Mapper
public interface RiskResultRepository {

    /**
     * 查询申请已有结果。
     *
     * @param applicationId 申请标识
     * @return 风控结果
     */
    Optional<RiskResult> findByApplicationId(@Param("applicationId") UUID applicationId);

    /**
     * 保存结果。
     *
     * @param result 风控结果
     * @return 受影响行数
     */
    int insert(RiskResult result);
}

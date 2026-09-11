package com.flowmesh.supplier.repository;

import com.flowmesh.supplier.domain.SupplierSupplement;
import org.apache.ibatis.annotations.Mapper;

/**
 * 使用 MyBatis 保存补件历史。
 */
@Mapper
public interface SupplierSupplementRepository {

    /**
     * 保存一轮补件历史。
     *
     * @param supplement 补件记录
     * @return 受影响行数
     */
    int insert(SupplierSupplement supplement);
}

package com.flowmesh.supplier.repository;

import com.flowmesh.supplier.domain.SupplierApplication;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 使用 MyBatis 访问供应商申请表。
 */
@Mapper
public interface SupplierApplicationRepository {

    /**
     * 插入供应商申请。
     *
     * @param application 待保存申请
     * @return 保存后的申请对象
     */
    default SupplierApplication saveAndFlush(SupplierApplication application) {
        insert(application);
        return application;
    }

    /**
     * 按申请标识查询当前租户可见的申请。
     *
     * @param id 申请标识
     * @return 供应商申请；不存在时为空
     */
    Optional<SupplierApplication> findById(@Param("id") UUID id);

    /**
     * 按实体版本条件更新申请状态。
     *
     * @param application 待更新申请
     * @return 受影响行数
     */
    int updateState(SupplierApplication application);

    /**
     * 插入申请记录。
     *
     * @param application 待保存申请
     * @return 受影响行数
     */
    int insert(SupplierApplication application);
}

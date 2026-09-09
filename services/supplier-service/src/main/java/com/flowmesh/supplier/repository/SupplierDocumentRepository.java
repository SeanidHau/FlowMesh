package com.flowmesh.supplier.repository;

import com.flowmesh.supplier.domain.SupplierDocument;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 使用 MyBatis 访问供应商材料元数据。
 */
@Mapper
public interface SupplierDocumentRepository {

    /**
     * 插入材料元数据。
     *
     * @param document 材料元数据
     * @return 受影响行数
     */
    int insert(SupplierDocument document);

    /**
     * 查询当前租户下指定申请的材料。
     *
     * @param applicationId 申请标识
     * @return 材料列表
     */
    List<SupplierDocument> findByApplicationId(@Param("applicationId") UUID applicationId);

    /**
     * 查询当前租户可见的单个材料。
     *
     * @param id 材料标识
     * @return 材料元数据
     */
    Optional<SupplierDocument> findById(@Param("id") UUID id);
}

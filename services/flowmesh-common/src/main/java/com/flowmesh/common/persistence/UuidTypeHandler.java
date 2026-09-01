package com.flowmesh.common.persistence;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.UUID;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

/**
 * 将 Java UUID 与 PostgreSQL uuid 类型互相转换。
 *
 * <p>MyBatis 默认不会为 {@link UUID} 注册 PostgreSQL 参数处理器，显式处理可以同时覆盖
 * UUID 主键、外键和可空 UUID 字段。</p>
 */
@MappedTypes(UUID.class)
public class UuidTypeHandler extends BaseTypeHandler<UUID> {

    /**
     * 将 UUID 作为 PostgreSQL {@code OTHER} 类型写入预编译语句。
     *
     * @param preparedStatement 预编译语句
     * @param index 参数位置
     * @param parameter UUID 参数
     * @param jdbcType JDBC 类型
     * @throws SQLException JDBC 写入失败
     */
    @Override
    public void setNonNullParameter(
        PreparedStatement preparedStatement,
        int index,
        UUID parameter,
        JdbcType jdbcType
    ) throws SQLException {
        preparedStatement.setObject(index, parameter, Types.OTHER);
    }

    /**
     * 从结果集按列名读取 UUID。
     *
     * @param resultSet 查询结果
     * @param columnName 列名
     * @return UUID；数据库值为空时返回 {@code null}
     * @throws SQLException JDBC 读取失败
     */
    @Override
    public UUID getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return toUuid(resultSet.getObject(columnName));
    }

    /**
     * 从结果集按列序号读取 UUID。
     *
     * @param resultSet 查询结果
     * @param columnIndex 列序号
     * @return UUID；数据库值为空时返回 {@code null}
     * @throws SQLException JDBC 读取失败
     */
    @Override
    public UUID getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return toUuid(resultSet.getObject(columnIndex));
    }

    /**
     * 从存储过程结果读取 UUID。
     *
     * @param callableStatement 存储过程调用
     * @param columnIndex 输出参数序号
     * @return UUID；数据库值为空时返回 {@code null}
     * @throws SQLException JDBC 读取失败
     */
    @Override
    public UUID getNullableResult(CallableStatement callableStatement, int columnIndex)
        throws SQLException {
        return toUuid(callableStatement.getObject(columnIndex));
    }

    private UUID toUuid(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof UUID uuid) {
            return uuid;
        }
        return UUID.fromString(value.toString());
    }
}

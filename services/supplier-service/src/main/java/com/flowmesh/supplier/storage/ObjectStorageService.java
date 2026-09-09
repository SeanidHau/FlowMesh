package com.flowmesh.supplier.storage;

import java.io.InputStream;

/**
 * 隔离对象存储 SDK，避免业务服务直接依赖具体厂商 API。
 */
public interface ObjectStorageService {

    /**
     * 保存材料对象。
     *
     * @param objectKey 对象键
     * @param content 文件内容
     * @param size 文件字节数
     * @param contentType 文件 MIME 类型
     */
    void put(String objectKey, InputStream content, long size, String contentType);

    /**
     * 删除对象，用于元数据落库失败后的补偿清理。
     *
     * @param objectKey 对象键
     */
    void delete(String objectKey);

    /**
     * 创建短期下载地址。
     *
     * @param objectKey 对象键
     * @return 预签名下载 URL
     */
    String createPresignedDownloadUrl(String objectKey);
}

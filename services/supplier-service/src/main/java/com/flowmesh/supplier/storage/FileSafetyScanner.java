package com.flowmesh.supplier.storage;

/**
 * 文件安全扫描抽象，便于替换 ClamAV 或云安全扫描服务。
 */
public interface FileSafetyScanner {

    /**
     * 扫描文件内容。
     *
     * @param content 文件字节
     * @return 扫描结果
     * @throws FileScanUnavailableException 扫描引擎不可用
     */
    FileScanResult scan(byte[] content);
}

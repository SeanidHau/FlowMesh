package com.flowmesh.supplier.storage;

import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;

/**
 * 通过 ClamAV INSTREAM 协议执行同步文件扫描。
 */
@Service
public class ClamAvFileSafetyScanner implements FileSafetyScanner {

    private static final int CHUNK_SIZE = 8 * 1024;
    private final FileScanProperties properties;

    /**
     * 创建 ClamAV 扫描器。
     *
     * @param properties 扫描配置
     */
    public ClamAvFileSafetyScanner(FileScanProperties properties) {
        this.properties = properties;
    }

    /**
     * 将内容通过 INSTREAM 协议发送给 ClamAV。
     *
     * @param content 文件字节
     * @return ClamAV 扫描结果
     */
    @Override
    public FileScanResult scan(byte[] content) {
        if (!properties.enabled()) {
            return FileScanResult.SKIPPED;
        }

        try (Socket socket = new Socket()) {
            int timeoutMillis = Math.toIntExact(properties.timeout().toMillis());
            socket.connect(new InetSocketAddress(properties.host(), properties.port()), timeoutMillis);
            socket.setSoTimeout(timeoutMillis);
            try (DataOutputStream output = new DataOutputStream(socket.getOutputStream())) {
                output.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
                try (ByteArrayInputStream input = new ByteArrayInputStream(content)) {
                    byte[] chunk = new byte[CHUNK_SIZE];
                    int read;
                    while ((read = input.read(chunk)) >= 0) {
                        if (read == 0) {
                            continue;
                        }
                        output.writeInt(read);
                        output.write(chunk, 0, read);
                    }
                }
                output.writeInt(0);
                output.flush();
            }

            String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.US_ASCII);
            if (response.contains("FOUND")) {
                return FileScanResult.INFECTED;
            }
            if (response.contains("OK")) {
                return FileScanResult.CLEAN;
            }
            throw new IOException("ClamAV 返回未知结果：" + response);
        } catch (IOException | ArithmeticException exception) {
            throw new FileScanUnavailableException("文件安全扫描服务不可用", exception);
        }
    }
}

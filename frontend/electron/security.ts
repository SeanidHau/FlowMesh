import path from 'node:path';
import { fileURLToPath } from 'node:url';

export interface TrustedSenderOptions {
  packaged: boolean;
  devServerUrl: string;
  packagedEntryPath: string;
}

/**
 * 校验 IPC 调用方是否来自当前应用加载的渲染页面。
 *
 * <p>开发模式比较完整 origin，打包模式只允许应用自己的入口文件，避免使用
 * 字符串前缀判断导致伪造 URL 绕过来源校验。</p>
 *
 * @param rawUrl 渲染帧当前 URL
 * @param options 当前运行模式和受信页面配置
 * @return 来源可信时为 {@code true}
 */
export function isTrustedSender(rawUrl: string, options: TrustedSenderOptions): boolean {
  try {
    const senderUrl = new URL(rawUrl);
    if (!options.packaged) {
      return senderUrl.origin === new URL(options.devServerUrl).origin;
    }

    return senderUrl.protocol === 'file:'
      && path.resolve(fileURLToPath(senderUrl)) === path.resolve(options.packagedEntryPath);
  } catch {
    return false;
  }
}

package com.flowmesh.iam.application.auth;

/**
 * 凭证无效时抛出的业务异常，由全局异常处理器映射为 401。
 */
public class InvalidCredentialsException extends RuntimeException {
}

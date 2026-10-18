package com.shiro.shiroaiagent.common;

/**
 * 统一错误返回
 *
 * @param code    业务/HTTP 状态码
 * @param message 错误说明（面向用户，不暴露堆栈）
 */
public record ApiError(int code, String message) {
}

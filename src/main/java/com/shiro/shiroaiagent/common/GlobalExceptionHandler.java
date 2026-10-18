package com.shiro.shiroaiagent.common;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：把鉴权与业务异常转成清晰的 HTTP 状态码与 JSON
 *
 * <p>答辩演示时很重要——"越权提问"会被明确拒绝（403），而不是抛 500 堆栈。</p>
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /** 未登录 / 登录过期 */
    @ExceptionHandler(NotLoginException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiError handleNotLogin(NotLoginException e) {
        log.warn("未登录访问被拦截：{}", e.getMessage());
        return new ApiError(401, "未登录或登录已过期，请先登录");
    }

    /** 无权限（角色 / 权限点不足） */
    @ExceptionHandler({NotRoleException.class, NotPermissionException.class})
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiError handleNoPermission(Exception e) {
        log.warn("权限不足：{}", e.getMessage());
        return new ApiError(403, "当前账号权限不足");
    }

    /** 越权访问他人数据（CurrentUserHolder.requireSelf 抛出） */
    @ExceptionHandler(SecurityException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiError handleSecurity(SecurityException e) {
        log.warn("越权访问被拒绝：{}", e.getMessage());
        return new ApiError(403, e.getMessage());
    }

    /**
     * 请求体无法反序列化：最常见于接口调试工具里 body 写错。
     *
     * <p>两种典型情况（都会命中这里）：</p>
     * <ol>
     *   <li>值没加双引号、或漏了收尾的花括号，例如 <code>{ "username": admin, "password": 123456</code></li>
     *   <li>整段 JSON 被当成"字符串"发送（调试工具/前端对 body 做了二次序列化）</li>
     * </ol>
     * <p>第 2 种在 Jackson 侧表现为
     * {@code MismatchedInputException: ... no String-argument constructor/factory method
     * to deserialize from String value}，看着像"后端 DTO 少了构造器"，
     * 其实是请求体在外面多包了一层引号。这里统一回 400 并给出正确示例，
     * 避免落到 {@link #handleOther} 变成一堆 500 堆栈。</p>
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleRequestBody(HttpMessageNotReadableException e) {
        Throwable root = e.getMostSpecificCause();
        log.warn("请求体解析失败：{}", root.getMessage());

        String example = "{\"username\":\"admin\",\"password\":\"123456\"}";
        String message;
        if (root instanceof MismatchedInputException
                && root.getMessage() != null
                && root.getMessage().contains("from String value")) {
            // 整段 body 被当成一个字符串值：调试工具里的"原始文本"模式常这样
            message = "请求体格式不对：整段 JSON 被当成一个字符串发送了"
                    + "（多半是调试工具的 body 又被包了一层引号），请直接提交 JSON 对象，例如 " + example;
        } else {
            message = "请求体解析失败：" + root.getClass().getSimpleName()
                    + "；请确认请求头 Content-Type: application/json，且 body 是合法 JSON 对象，例如 " + example;
        }
        return new ApiError(400, message);
    }

    /** 参数不合法（含登录失败：账号或密码错误） */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleIllegalArgument(IllegalArgumentException e) {
        log.warn("参数异常：{}", e.getMessage());
        return new ApiError(400, e.getMessage());
    }

    /** 兜底 */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiError handleOther(Exception e) {
        return buildDetailed500(e);
    }

    /**
     * 诊断辅助：默认 500 只返回通用文案，排查困难。
     * 这里在响应头里带上异常类型与消息（不含堆栈、不含敏感数据），
     * 便于开发期快速定位。生产环境可通过配置关闭。
     */
    private ApiError buildDetailed500(Exception e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String detail = root.getClass().getSimpleName() + ": " + root.getMessage();
        log.error("服务异常（根因：{}）", detail, e);
        return new ApiError(500, "服务异常：" + detail);
    }
}

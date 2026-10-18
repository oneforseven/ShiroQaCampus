package com.shiro.shiroaiagent.security;

import cn.dev33.satoken.stp.StpUtil;

/**
 * 当前登录身份的唯一入口。
 *
 * <p><b>关键设计（论文"安全设计"一节的落点）：</b>
 * 所有个人数据的查询都通过这里取身份，业务方法<b>绝不接收前端或模型传入的学号</b>，
 * 从接口层面杜绝"学生 A 问出学生 B 的成绩"。</p>
 *
 * <p><b>身份来源：</b>Sa-Token 会话中的 {@link LoginUser}，由 {@code AuthService#login} 写入。</p>
 *
 * <p><b>⚠️ 流式问答的坑（下一步接 AI 时必须处理）：</b>
 * Sa-Token 的上下文基于 ThreadLocal，而 Flux / SseEmitter 的流式输出运行在<b>其他线程</b>上，
 * 在那里调用 {@link #currentStudentId()} 会拿不到身份。正确做法是：
 * 在<b>请求线程</b>里先取出身份（{@link #currentLoginUser()} 或 {@link #currentStudentId()}），
 * 再作为参数显式传进异步链路与工具方法。</p>
 */
public final class CurrentUserHolder {

    /** 会话中存放登录用户的 key */
    public static final String SESSION_LOGIN_USER = "loginUser";

    private CurrentUserHolder() {
    }

    /**
     * 当前登录用户
     *
     * @throws cn.dev33.satoken.exception.NotLoginException 未登录或登录已过期（全局异常处理转为 401）
     */
    public static LoginUser currentLoginUser() {
        return (LoginUser) StpUtil.getSession().get(SESSION_LOGIN_USER);
    }

    /**
     * 当前登录学生的 student_id
     *
     * @throws SecurityException 当前登录账号不是学生账号
     */
    public static Long currentStudentId() {
        LoginUser user = currentLoginUser();
        if (user == null || user.getStudentId() == null) {
            throw new SecurityException("当前登录账号不是学生账号，无法查询学生个人数据");
        }
        return user.getStudentId();
    }

    /**
     * 当前登录学生的学号（用于日志与来源标注，不作为查询参数）
     */
    public static String currentStudentNo() {
        LoginUser user = currentLoginUser();
        return user == null ? null : user.getStudentNo();
    }

    /**
     * 越权校验：被访问的数据必须属于当前登录学生本人
     *
     * @param studentId 被访问数据所属的学生 id
     */
    public static void requireSelf(Long studentId) {
        if (studentId == null || !studentId.equals(currentStudentId())) {
            throw new SecurityException("无权访问他人数据");
        }
    }
}

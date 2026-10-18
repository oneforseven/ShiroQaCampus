package com.shiro.shiroaiagent.security;

import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Sa-Token 权限数据源：告诉框架"当前登录用户拥有哪些角色/权限"
 *
 * <p><b>为什么需要这个类：</b>登录时我们把角色信息放在了自定义的 {@link LoginUser} 里，
 * 但 Sa-Token 的 {@code StpUtil.checkRole()} / {@code @SaCheckRole} 走的是它自己的
 * 数据源接口 {@link StpInterface}。不实现它，{@code checkRole} 就永远查不到角色，
 * 一律判定"权限不足"（表现为 403）。</p>
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    /**
     * 返回当前账号的角色标识列表，如 [super_admin]
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        LoginUser user = currentUser();
        if (user == null || user.getRoleCodes() == null) {
            return Collections.emptyList();
        }
        return user.getRoleCodes();
    }

    /**
     * 返回当前账号的权限点列表
     *
     * <p>本项目采用"角色控制"粒度（学生 / 部门管理员 / 超级管理员），
     * 尚未细分到权限点，故返回空列表。若后续需要更细的权限控制，
     * 可在此按角色展开权限点，例如 super_admin → ["kb:ingest", "student:edit"]。</p>
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return Collections.emptyList();
    }

    /**
     * 从 Sa-Token 会话中取出登录用户
     */
    private LoginUser currentUser() {
        Object value = StpUtil.getSession().get(CurrentUserHolder.SESSION_LOGIN_USER);
        return value instanceof LoginUser user ? user : null;
    }
}

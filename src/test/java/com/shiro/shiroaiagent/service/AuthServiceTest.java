package com.shiro.shiroaiagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.test.autoconfigure.MybatisPlusTest;
import com.shiro.shiroaiagent.config.MybatisPlusConfig;
import com.shiro.shiroaiagent.entity.SysUser;
import com.shiro.shiroaiagent.mapper.SysUserMapper;
import com.shiro.shiroaiagent.security.LoginUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 登录鉴权核心逻辑测试
 *
 * 只测不依赖 Web 上下文的 {@code authenticate}：查账号、BCrypt 校验、组装角色与学号。
 * token 签发（{@code login}）依赖 Sa-Token 的请求上下文，放到接口层验证。
 */
@MybatisPlusTest
@Import({MybatisPlusConfig.class, AuthService.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("登录鉴权：账号密码校验与身份组装")
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Test
    @DisplayName("学生用学号 + 正确密码登录：带出 student_id、学号、角色与学院")
    void studentLoginSuccess() {
        LoginUser user = authService.authenticate("2022001", "123456");

        assertNotNull(user);
        assertEquals("张伟", user.getRealName());
        assertEquals("student", user.getUserType());
        assertEquals(1L, user.getStudentId(), "student_id 应来自登录账号绑定，而非前端传入");
        assertEquals("2022001", user.getStudentNo());
        assertEquals("计算机学院", user.getDeptName());
        assertTrue(user.getRoleCodes().contains("student"));
        assertTrue(user.isStudent());
    }

    @Test
    @DisplayName("管理员登录：没有 student_id，不能查学生个人数据")
    void adminLoginSuccess() {
        LoginUser user = authService.authenticate("admin", "123456");

        assertNotNull(user);
        assertEquals("系统管理员", user.getRealName());
        assertEquals("admin", user.getUserType());
        assertNull(user.getStudentId());
        assertTrue(user.getRoleCodes().contains("super_admin"));
        assertEquals("教务处", user.getDeptName());
    }

    @Test
    @DisplayName("密码错误：拒绝登录，且提示不暴露账号是否存在")
    void wrongPasswordRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> authService.authenticate("2022001", "wrong-password"));
        assertEquals("账号或密码错误", e.getMessage());
    }

    @Test
    @DisplayName("账号不存在：与密码错误返回同样的提示")
    void unknownUserRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> authService.authenticate("no-such-user", "123456"));
        assertEquals("账号或密码错误", e.getMessage());
    }

    @Test
    @DisplayName("空账号或空密码：直接拒绝")
    void blankInputRejected() {
        assertThrows(IllegalArgumentException.class, () -> authService.authenticate("", "123456"));
        assertThrows(IllegalArgumentException.class, () -> authService.authenticate("2022001", ""));
    }

    @Test
    @DisplayName("更新登录时间：不得破坏 username/password/user_type 等关键字段")
    void updateLastLoginAtKeepsOtherColumnsIntact() {
        SysUser before = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, "2022001"));
        assertNotNull(before);
        String passwordBefore = before.getPassword();
        String userTypeBefore = before.getUserType();

        // 与 AuthService.login() 中一致的正确写法：只更新指定字段
        int updated = sysUserMapper.update(null,
                Wrappers.<SysUser>lambdaUpdate()
                        .eq(SysUser::getId, before.getId())
                        .set(SysUser::getLastLoginAt, java.time.OffsetDateTime.now()));
        assertEquals(1, updated, "应更新 1 行");

        SysUser after = sysUserMapper.selectById(before.getId());
        assertNotNull(after);
        assertEquals("2022001", after.getUsername(), "username 不得被置空");
        assertEquals(passwordBefore, after.getPassword(), "password 不得被清空");
        assertEquals(userTypeBefore, after.getUserType(), "user_type 不得被置空");
        assertNotNull(after.getLastLoginAt(), "登录时间应已更新");
    }

    @Test
    @DisplayName("验证 updateById 的行为：MyBatis-Plus 默认只更新非 null 字段（不会置空其他列）")
    void updateByIdWithPartialEntityOnlyUpdatesNonNullFields() {
        SysUser before = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, "2022002"));
        assertNotNull(before);
        String passwordBefore = before.getPassword();
        String userTypeBefore = before.getUserType();

        SysUser partial = new SysUser();
        partial.setId(before.getId());
        partial.setLastLoginAt(java.time.OffsetDateTime.now());

        // MyBatis-Plus 默认的字段更新策略是 NOT_NULL：
        // 只把非 null 字段拼进 UPDATE 语句，因此不会误伤其他列。
        sysUserMapper.updateById(partial);

        SysUser after = sysUserMapper.selectById(before.getId());
        assertNotNull(after);
        assertEquals("2022002", after.getUsername(), "username 不应被改动");
        assertEquals(passwordBefore, after.getPassword(), "password 不应被清空");
        assertEquals(userTypeBefore, after.getUserType(), "user_type 不应被置空");
        assertNotNull(after.getLastLoginAt(), "登录时间应已更新");
    }
}

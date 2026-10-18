package com.shiro.shiroaiagent.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shiro.shiroaiagent.entity.Student;
import com.shiro.shiroaiagent.entity.SysDept;
import com.shiro.shiroaiagent.entity.SysUser;
import com.shiro.shiroaiagent.mapper.StudentMapper;
import com.shiro.shiroaiagent.mapper.SysDeptMapper;
import com.shiro.shiroaiagent.mapper.SysRoleMapper;
import com.shiro.shiroaiagent.mapper.SysUserMapper;
import com.shiro.shiroaiagent.security.CurrentUserHolder;
import com.shiro.shiroaiagent.security.LoginUser;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 登录鉴权服务
 *
 * <p>密码用 Hutool 的 BCrypt 校验（与 {@code sql/02-mock-data.sql} 中的哈希一致），
 * 登录成功后由 Sa-Token 签发 token，并把 {@link LoginUser} 写入会话。</p>
 *
 * <p><b>设计要点：</b>{@link #authenticate} 是不依赖 Web 上下文的纯校验逻辑，
 * 便于单元测试；{@link #login} 在其之上完成 token 签发。</p>
 */
@Service
@Slf4j
public class AuthService {

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private SysRoleMapper sysRoleMapper;

    @Resource
    private SysDeptMapper sysDeptMapper;

    @Resource
    private StudentMapper studentMapper;

    /**
     * 校验账号密码，返回登录用户信息（不签发 token）
     *
     * @throws IllegalArgumentException 账号或密码错误
     * @throws SecurityException        账号被禁用
     */
    public LoginUser authenticate(String username, String rawPassword) {
        if (StrUtil.isBlank(username) || StrUtil.isBlank(rawPassword)) {
            throw new IllegalArgumentException("账号与密码不能为空");
        }

        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        // 账号不存在与密码错误返回同样的提示，避免暴露账号是否存在
        if (user == null) {
            throw new IllegalArgumentException("账号或密码错误");
        }
        if (Integer.valueOf(0).equals(user.getStatus())) {
            throw new SecurityException("该账号已被禁用，请联系管理员");
        }
        if (!BCrypt.checkpw(rawPassword, user.getPassword())) {
            throw new IllegalArgumentException("账号或密码错误");
        }

        return buildLoginUser(user);
    }

    /**
     * 登录：校验通过后签发 token，并写入会话
     * @param username
     * @param rawPassword
     * @return
     */
    public LoginUser login(String username, String rawPassword) {
        LoginUser loginUser = authenticate(username, rawPassword);

        StpUtil.login(loginUser.getUserId());
        StpUtil.getSession().set(CurrentUserHolder.SESSION_LOGIN_USER, loginUser);

        // 记录最后登录时间
        // ⚠️ 注意：不能用 updateById(entity) ——MyBatis-Plus 默认会把对象中
        //    未 set 的字段一并更新为 NULL，而 sys_user 的 username/password/user_type
        //    都是 NOT NULL，会直接违反数据库约束导致登录 500。
        //    正确做法：用 LambdaUpdateWrapper 只更新明确指定的字段。
        sysUserMapper.update(null,
                Wrappers.<SysUser>lambdaUpdate()
                        .eq(SysUser::getId, loginUser.getUserId())
                        .set(SysUser::getLastLoginAt, OffsetDateTime.now()));

        log.info("登录成功：username={}, userType={}, roles={}, studentNo={}",
                loginUser.getUsername(), loginUser.getUserType(),
                loginUser.getRoleCodes(), loginUser.getStudentNo());
        return loginUser;
    }

    /**
     * 登出
     */
    public void logout() {
        Object loginId = StpUtil.getLoginIdDefaultNull();
        StpUtil.logout();
        log.info("登出：loginId={}", loginId);
    }

    /**
     * 组装登录用户信息：角色、部门、学生身份（学号 / student_id）
     * @param user
     * @return
     */
    private LoginUser buildLoginUser(SysUser user) {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(user.getId());
        loginUser.setUsername(user.getUsername());
        loginUser.setRealName(user.getRealName());
        loginUser.setUserType(user.getUserType());
        loginUser.setDeptId(user.getDeptId());

        if (user.getDeptId() != null) {
            SysDept dept = sysDeptMapper.selectById(user.getDeptId());
            if (dept != null) {
                loginUser.setDeptName(dept.getName());
            }
        }

        List<String> roleCodes = sysRoleMapper.selectRoleCodesByUserId(user.getId());
        loginUser.setRoleCodes(roleCodes);

        // 学生账号：带出 student_id 与学号（后续查成绩、工具调用都用它，避免前端传学号）
        Student student = studentMapper.selectOne(
                new LambdaQueryWrapper<Student>().eq(Student::getUserId, user.getId()));
        if (student != null) {
            loginUser.setStudentId(student.getId());
            loginUser.setStudentNo(student.getStudentNo());
        }

        return loginUser;
    }
}

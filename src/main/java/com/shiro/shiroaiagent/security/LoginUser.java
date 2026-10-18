package com.shiro.shiroaiagent.security;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 登录用户（存放在 Sa-Token 会话中）
 *
 * <p>这是"身份"在系统中的载体：接口层、业务层都从这里取当前用户，
 * <b>不接受前端传入的身份参数</b>。</p>
 */
@Data
public class LoginUser implements Serializable {

    /** sys_user.id */
    private Long userId;

    /** 登录名（学生为学号） */
    private String username;

    private String realName;

    /** student / teacher / admin */
    private String userType;

    private Long deptId;

    /** 部门（学院）名称，便于直接展示 */
    private String deptName;

    /** 角色标识列表，如 [student]、[super_admin] */
    private List<String> roleCodes;

    /** 学生账号才有：student.id */
    private Long studentId;

    /** 学生账号才有：学号 */
    private String studentNo;

    /** 是否为学生账号 */
    public boolean isStudent() {
        return studentId != null;
    }
}

package com.shiro.shiroaiagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 账号（表 sys_user）
 * <p>登录鉴权的主体。学生账号与 {@link Student} 一对一。</p>
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录名（学生用学号） */
    private String username;

    /** BCrypt 哈希，禁止存明文 */
    private String password;

    private String realName;

    /** 账号类型：student / teacher / admin */
    private String userType;

    /** 所属部门 */
    private Long deptId;

    private String phone;

    private String email;

    /** 1 正常 0 禁用 */
    private Integer status;

    private OffsetDateTime lastLoginAt;

    private OffsetDateTime createdAt;
}

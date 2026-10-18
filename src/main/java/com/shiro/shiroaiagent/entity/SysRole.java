package com.shiro.shiroaiagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 角色（表 sys_role）
 * <p>student 学生 / teacher 教师 / dept_admin 部门管理员 / super_admin 超级管理员</p>
 */
@Data
@TableName("sys_role")
public class SysRole {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色标识 */
    private String code;

    /** 角色名称 */
    private String name;

    private String remark;

    private Integer status;

    private OffsetDateTime createdAt;
}

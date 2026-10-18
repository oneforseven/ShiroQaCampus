package com.shiro.shiroaiagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 学生（表 student）
 * <p><b>个人数据源</b>：只允许经鉴权后由工具实时查询，<b>绝不进入向量库</b>。</p>
 */
@Data
@TableName("student")
public class Student {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联登录账号 */
    private Long userId;

    private String studentNo;

    private String name;

    private String gender;

    private Long deptId;

    private Long majorId;

    private String className;

    private Integer enrollYear;

    /** 年级，如 2022级 */
    private String grade;

    /** 平均绩点（由 score 按规则汇总回填） */
    private BigDecimal gpa;

    /** 已获得学分 */
    private BigDecimal earnedCredits;

    /** 1 在读 2 休学 3 毕业 0 退学 */
    private Integer status;

    private OffsetDateTime createdAt;
}

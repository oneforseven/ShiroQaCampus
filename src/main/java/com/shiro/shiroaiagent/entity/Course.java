package com.shiro.shiroaiagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 课程（表 course）
 */
@Data
@TableName("course")
public class Course {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private String name;

    /** 学分 */
    private BigDecimal credit;

    /** 学时 */
    private Integer hours;

    /** 必修 / 选修 / 实践 */
    private String courseType;

    /** 考试 / 考查 */
    private String assessType;

    /** 开课单位 */
    private Long deptId;

    private Integer status;

    private OffsetDateTime createdAt;
}

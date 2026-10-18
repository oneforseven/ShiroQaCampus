package com.shiro.shiroaiagent.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 成绩单条目：成绩 + 课程信息
 */
@Data
public class ScoreVO {

    private Long id;

    private String courseCode;

    private String courseName;

    private BigDecimal credit;

    /** 必修 / 选修 / 实践 */
    private String courseType;

    /** 考试 / 考查 */
    private String assessType;

    /** 学期，如 2023-2024-1 */
    private String term;

    private BigDecimal score;

    /** 该课绩点 */
    private BigDecimal gpaPoint;

    /** 正常考试 / 补考 / 重修 / 缓考 */
    private String examType;

    private Boolean passed;
}

package com.shiro.shiroaiagent.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 按课程性质统计的已获学分（同一门课多次修读取最高分那次）
 */
@Data
public class CreditStatVO {

    /** 必修 / 选修 / 实践 */
    private String courseType;

    /** 已获得学分 */
    private BigDecimal earnedCredits;

    /** 已通过门数 */
    private Integer courseCount;
}

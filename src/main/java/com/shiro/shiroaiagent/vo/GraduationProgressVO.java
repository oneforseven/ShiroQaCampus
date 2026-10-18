package com.shiro.shiroaiagent.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 毕业学分进度：专业要求 vs 已获得 vs 还差多少
 * <p>支撑"我还差多少学分能毕业""必修课修够了吗"这类问题。</p>
 */
@Data
public class GraduationProgressVO {

    private String majorName;

    /** 毕业要求总学分 */
    private BigDecimal totalCredits;

    /** 毕业要求必修学分 */
    private BigDecimal requiredCredits;

    /** 毕业要求选修学分 */
    private BigDecimal electiveCredits;

    /** 已获得总学分 */
    private BigDecimal earnedCredits;

    /** 已获得必修学分 */
    private BigDecimal earnedRequiredCredits;

    /** 已获得选修学分 */
    private BigDecimal earnedElectiveCredits;

    /** 还差多少学分 */
    private BigDecimal remainingCredits;

    /** 已通过门数 */
    private Integer passedCourseCount;

    /** 分类明细 */
    private List<CreditStatVO> detail;
}

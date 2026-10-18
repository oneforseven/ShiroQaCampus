package com.shiro.shiroaiagent.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 学生本人信息视图：学生 + 专业（含毕业学分要求）+ 学院
 * <p>字段与 {@code student}、{@code major}、{@code sys_dept} 三表联查结果对应。</p>
 */
@Data
public class StudentProfileVO {

    private Long id;

    private String studentNo;

    private String name;

    private String gender;

    private String className;

    /** 年级，如 2023级 */
    private String grade;

    private Integer enrollYear;

    /** 平均绩点 */
    private BigDecimal gpa;

    /** 已获得学分 */
    private BigDecimal earnedCredits;

    /** 1 在读 2 休学 3 毕业 0 退学 */
    private Integer status;

    /** 学院名称 */
    private String deptName;

    private String majorCode;

    private String majorName;

    private Integer durationYears;

    /** 毕业要求总学分 */
    private BigDecimal totalCredits;

    /** 毕业要求必修学分 */
    private BigDecimal requiredCredits;

    /** 毕业要求选修学分 */
    private BigDecimal electiveCredits;
}

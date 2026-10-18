package com.shiro.shiroaiagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 成绩（表 score）
 * <p>个人数据核心表。同一门课可有多条（补考 / 重修），业务查询取最高分那一次。</p>
 */
@Data
@TableName("score")
public class Score {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long studentId;

    private Long courseId;

    /** 学期，如 2023-2024-1 */
    private String term;

    private BigDecimal score;

    /** 该课绩点（按 gpa_rule 换算） */
    private BigDecimal gpaPoint;

    /** 正常考试 / 补考 / 重修 / 缓考 */
    private String examType;

    /** 是否通过（补考通过也记 true） */
    private Boolean passed;

    private String remark;

    private OffsetDateTime createdAt;
}

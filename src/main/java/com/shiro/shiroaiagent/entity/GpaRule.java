package com.shiro.shiroaiagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 绩点换算规则（表 gpa_rule）
 * <p>"绩点怎么算"既要能查规则（可进知识库），也要能算结果（本表用于换算）。</p>
 */
@Data
@TableName("gpa_rule")
public class GpaRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分数下限 */
    private BigDecimal scoreMin;

    /** 分数上限 */
    private BigDecimal scoreMax;

    private BigDecimal gpaPoint;

    /** 优秀 / 良好 / 中等 / 及格 / 不及格 */
    private String gradeLabel;

    private String remark;
}

package com.shiro.shiroaiagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 专业（表 major）
 * <p>含毕业学分要求，供"毕业要求 / 还差多少学分"类问题查询。</p>
 */
@Data
@TableName("major")
public class Major {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private String name;

    private Long deptId;

    /** 学制（年） */
    private Integer durationYears;

    /** 毕业要求总学分 */
    private BigDecimal totalCredits;

    /** 其中必修学分 */
    private BigDecimal requiredCredits;

    /** 其中选修学分 */
    private BigDecimal electiveCredits;

    private String remark;

    private OffsetDateTime createdAt;
}

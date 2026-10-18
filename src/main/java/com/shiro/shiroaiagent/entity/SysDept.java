package com.shiro.shiroaiagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 部门 / 院系 / 校内机构（表 sys_dept）
 * <p>知识库可见范围与权限划分的基准单位。</p>
 */
@Data
@TableName("sys_dept")
public class SysDept {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 父部门 id，0 表示顶级 */
    private Long parentId;

    private String name;

    private String code;

    /** 学院 / 教务处 / 学工部 / 图书馆 / 就业中心 / 网络中心 */
    private String deptType;

    private Integer sortNo;

    private Integer status;

    private OffsetDateTime createdAt;
}

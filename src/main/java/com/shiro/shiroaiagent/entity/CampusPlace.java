package com.shiro.shiroaiagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 校园办事地点（表 campus_place）
 * <p>校园导航类问题（去哪办、几点办公、电话多少）走此表的结构化查询，不进知识库。</p>
 */
@Data
@TableName("campus_place")
public class CampusPlace {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 教务处 / 学工部 / 图书馆 / 食堂 / 宿舍 / 网络中心 / 就业中心 */
    private String category;

    private Long deptId;

    private String building;

    private String room;

    private String address;

    /** 办公时间 */
    private String officeHours;

    private String phone;

    /** 检索关键词，如"补办学生证 缓考 成绩单" */
    private String keywords;

    private String remark;

    private Integer status;

    private OffsetDateTime createdAt;
}

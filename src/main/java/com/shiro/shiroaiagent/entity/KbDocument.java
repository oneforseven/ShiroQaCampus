package com.shiro.shiroaiagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 知识库文档（表 kb_document）
 *
 * 这张表是"入库"的唯一真相来源：文档从哪来、属于哪个场景、谁能看见、
 * 审核走到哪一步、向量是否已写入，全部记在这里。
 */
@Data
@TableName("kb_document")
public class KbDocument {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 展示标题：前端"引用来源"显示的就是它 */
    private String title;

    /** 原始文件名（上传时的名字，保留给管理员辨认） */
    private String fileName;

    /** 落盘路径（tmp/kb 下）：重新入库、删除都靠它找回文件 */
    private String filePath;

    /** md（当前只支持 Markdown） */
    private String fileType;

    private Long fileSize;

    /** 场景分类，六大场景之一 */
    private String scene;

    /** 归属部门（权限下推的预留字段） */
    private Long deptId;

    /** 可见范围：公开 / 本部门 / 指定角色（本轮只落元数据，尚未参与检索过滤） */
    private String visibility;

    /** 待审核 / 已通过 / 已驳回 / 已下架 */
    private String status;

    private Long uploaderId;

    /** 管理员 / 系统 */
    private String uploaderType;

    private Long reviewerId;

    private OffsetDateTime reviewedAt;

    private String reviewRemark;

    /** 切分后的片段数（入库成功后回填） */
    private Integer chunkCount;

    /** 未入库 / 已入库 / 入库失败 */
    private String vectorStatus;

    private LocalDate effectiveDate;

    /** 入库次数：每次审核通过或重建 +1，便于追溯"这篇灌过几遍" */
    private Integer version;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}

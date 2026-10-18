package com.shiro.shiroaiagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 知识库文档审核记录（表 kb_document_review）
 *
 * 每次审核动作留一条记录：提交 / 通过 / 驳回 / 下架。
 *
 * 文档被物理删除时，本表记录由数据库外键 {@code ON DELETE CASCADE} 一并清理。
 */
@Data
@TableName("kb_document_review")
public class KbDocumentReview {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long documentId;

    private Long reviewerId;

    /** 提交 / 通过 / 驳回 / 下架 */
    private String action;

    private String remark;

    private OffsetDateTime createdAt;
}

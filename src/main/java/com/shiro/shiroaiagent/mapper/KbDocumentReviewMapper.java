package com.shiro.shiroaiagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shiro.shiroaiagent.entity.KbDocumentReview;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库文档审核记录数据访问
 */
@Mapper
public interface KbDocumentReviewMapper extends BaseMapper<KbDocumentReview> {
}

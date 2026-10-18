package com.shiro.shiroaiagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shiro.shiroaiagent.entity.KbDocument;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库文档数据访问
 *
 */
@Mapper
public interface KbDocumentMapper extends BaseMapper<KbDocument> {
}

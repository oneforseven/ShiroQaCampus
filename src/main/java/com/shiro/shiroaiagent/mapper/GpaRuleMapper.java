package com.shiro.shiroaiagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shiro.shiroaiagent.entity.GpaRule;
import org.apache.ibatis.annotations.Mapper;

/**
 * 绩点规则数据访问
 */
@Mapper
public interface GpaRuleMapper extends BaseMapper<GpaRule> {
}

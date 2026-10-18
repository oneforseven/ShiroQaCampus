package com.shiro.shiroaiagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shiro.shiroaiagent.entity.Major;
import org.apache.ibatis.annotations.Mapper;

/**
 * 专业数据访问
 */
@Mapper
public interface MajorMapper extends BaseMapper<Major> {
}

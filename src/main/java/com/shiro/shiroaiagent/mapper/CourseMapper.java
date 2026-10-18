package com.shiro.shiroaiagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shiro.shiroaiagent.entity.Course;
import org.apache.ibatis.annotations.Mapper;

/**
 * 课程数据访问
 */
@Mapper
public interface CourseMapper extends BaseMapper<Course> {
}

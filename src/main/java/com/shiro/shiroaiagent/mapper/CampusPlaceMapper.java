package com.shiro.shiroaiagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shiro.shiroaiagent.entity.CampusPlace;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 校园办事地点数据访问（校园导航）
 */
@Mapper
public interface CampusPlaceMapper extends BaseMapper<CampusPlace> {

    /**
     * 关键词检索办事地点：命中名称、关键词、分类或楼栋
     */
    @Select("""
            <script>
            SELECT * FROM campus_place
            WHERE status = 1
            <if test="keyword != null and keyword != ''">
                AND (name LIKE CONCAT('%', #{keyword}, '%')
                     OR keywords LIKE CONCAT('%', #{keyword}, '%')
                     OR category LIKE CONCAT('%', #{keyword}, '%')
                     OR building LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            ORDER BY id
            </script>
            """)
    List<CampusPlace> search(@Param("keyword") String keyword);
}

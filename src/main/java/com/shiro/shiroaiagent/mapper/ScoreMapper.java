package com.shiro.shiroaiagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shiro.shiroaiagent.entity.Score;
import com.shiro.shiroaiagent.vo.CreditStatVO;
import com.shiro.shiroaiagent.vo.ScoreVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 成绩数据访问
 */
@Mapper
public interface ScoreMapper extends BaseMapper<Score> {

    /**
     * 查询某学生的成绩单（可按学期过滤；term 为空则查全部学期）
     * <p>注意：同一门课若有多条（补考/重修）会全部返回，由业务层决定如何呈现。</p>
     */
    @Select("""
            <script>
            SELECT sc.id, c.code AS course_code, c.name AS course_name, c.credit,
                   c.course_type, c.assess_type, sc.term, sc.score, sc.gpa_point,
                   sc.exam_type, sc.passed
            FROM score sc
            JOIN course c ON c.id = sc.course_id
            WHERE sc.student_id = #{studentId}
            <if test="term != null and term != ''">
                AND sc.term = #{term}
            </if>
            ORDER BY sc.term DESC, c.code
            </script>
            """)
    List<ScoreVO> selectMyScores(@Param("studentId") Long studentId, @Param("term") String term);

    /**
     * 按课程性质统计已获学分（同一门课多次修读时，取分数最高的那一次）
     */
    @Select("""
            SELECT c.course_type        AS course_type,
                   COALESCE(SUM(c.credit), 0) AS earned_credits,
                   COUNT(*)             AS course_count
            FROM (
                SELECT DISTINCT ON (sc.course_id) sc.course_id, sc.passed
                FROM score sc
                WHERE sc.student_id = #{studentId}
                ORDER BY sc.course_id, sc.score DESC
            ) b
            JOIN course c ON c.id = b.course_id
            WHERE b.passed = TRUE
            GROUP BY c.course_type
            """)
    List<CreditStatVO> selectEarnedCreditStats(@Param("studentId") Long studentId);
}

package com.shiro.shiroaiagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shiro.shiroaiagent.entity.Student;
import com.shiro.shiroaiagent.vo.StudentProfileVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 学生数据访问
 */
@Mapper
public interface StudentMapper extends BaseMapper<Student> {

    /**
     * 查询学生本人信息（联查专业与学院，带出毕业学分要求）
     */
    @Select("""
            SELECT s.id, s.student_no, s.name, s.gender, s.class_name, s.grade, s.enroll_year,
                   s.gpa, s.earned_credits, s.status,
                   m.code AS major_code, m.name AS major_name, m.duration_years,
                   m.total_credits, m.required_credits, m.elective_credits,
                   d.name AS dept_name
            FROM student s
            LEFT JOIN major m ON m.id = s.major_id
            LEFT JOIN sys_dept d ON d.id = s.dept_id
            WHERE s.id = #{studentId}
            """)
    StudentProfileVO selectProfile(@Param("studentId") Long studentId);
}

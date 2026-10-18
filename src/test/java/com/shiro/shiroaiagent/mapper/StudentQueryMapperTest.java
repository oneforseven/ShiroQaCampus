package com.shiro.shiroaiagent.mapper;

import com.baomidou.mybatisplus.test.autoconfigure.MybatisPlusTest;
import com.shiro.shiroaiagent.config.MybatisPlusConfig;
import com.shiro.shiroaiagent.entity.CampusPlace;
import com.shiro.shiroaiagent.vo.CreditStatVO;
import com.shiro.shiroaiagent.vo.ScoreVO;
import com.shiro.shiroaiagent.vo.StudentProfileVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 业务库 Mapper 切片测试
 *
 * 为什么用切片测试：</b>整个应用启动涉及大模型与向量库装配，
 * 必须提供有效的 DashScope Key；数据层的验证不应该依赖它，因此这里只加载 MyBatis-Plus 与数据源。
 *
 * 前置条件：已执行 {@code sql/01-schema.sql} 与 {@code sql/02-mock-data.sql}，
 * 且 {@code application-local.yml} 指向的 PostgreSQL 可访问。
 */
@MybatisPlusTest
@Import(MybatisPlusConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("业务库 Mapper：学生画像 / 成绩单 / 学分统计 / 校园导航")
class StudentQueryMapperTest {

    @Autowired
    private StudentMapper studentMapper;

    @Autowired
    private ScoreMapper scoreMapper;

    @Autowired
    private CampusPlaceMapper campusPlaceMapper;

    @Test
    @DisplayName("查询学生画像：联查专业与学院，带出毕业学分要求")
    void selectProfile() {
        StudentProfileVO profile = studentMapper.selectProfile(1L);

        assertNotNull(profile, "student_id=1 应存在（张伟）");
        assertEquals("2022001", profile.getStudentNo());
        assertEquals("张伟", profile.getName());
        assertEquals("计科2201", profile.getClassName());
        // 下划线转驼峰映射是否生效
        assertEquals("计算机科学与技术", profile.getMajorName());
        assertEquals("计算机学院", profile.getDeptName());
        assertEquals(0, new BigDecimal("160.0").compareTo(profile.getTotalCredits()));
        // 绩点应是脚本按 gpa_rule 汇总出来的结果
        assertEquals(0, new BigDecimal("3.34").compareTo(profile.getGpa()));
    }

    @Test
    @DisplayName("查询成绩单：无学期条件返回全部，指定学期则过滤")
    void selectMyScores() {
        // 陈晓（student_id=5）共 8 条记录，其中高等数学有正常考试与重修两条
        List<ScoreVO> all = scoreMapper.selectMyScores(5L, null);
        assertEquals(8, all.size());
        long mathCount = all.stream().filter(s -> "高等数学(上)".equals(s.getCourseName())).count();
        assertEquals(2, mathCount, "重修会保留两条记录，由业务层决定呈现方式");
        assertTrue(all.stream().anyMatch(s -> "重修".equals(s.getExamType())));

        List<ScoreVO> firstTerm = scoreMapper.selectMyScores(5L, "2022-2023-1");
        assertEquals(3, firstTerm.size());
        assertTrue(firstTerm.stream().allMatch(s -> "2022-2023-1".equals(s.getTerm())));
        // course_type / assess_type 等下划线字段也应正确映射
        assertTrue(firstTerm.stream().anyMatch(s -> "必修".equals(s.getCourseType())));
    }

    @Test
    @DisplayName("学分统计：同一门课取最高分那次，只统计已通过课程")
    void selectEarnedCreditStats() {
        // 陈晓：高数 55 分挂科后重修 68 分通过 → 应按通过计算，且不重复计学分
        List<CreditStatVO> stats = scoreMapper.selectEarnedCreditStats(5L);

        assertFalse(stats.isEmpty());
        CreditStatVO required = stats.stream()
                .filter(s -> "必修".equals(s.getCourseType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("应有必修学分统计"));
        // 5 + 4 + 4 + 3.5 + 4 + 3.5 + 3.5 = 27.5，共 7 门通过
        assertEquals(0, new BigDecimal("27.5").compareTo(required.getEarnedCredits()));
        assertEquals(7, required.getCourseCount());
    }

    @Test
    @DisplayName("校园导航：按关键词检索办事地点")
    void searchCampusPlace() {
        List<CampusPlace> places = campusPlaceMapper.search("学生证");
        assertFalse(places.isEmpty(), "关键词 学生证 应命中教务处学籍科");
        assertTrue(places.stream().anyMatch(p -> "教务处学籍科".equals(p.getName())));

        // 关键词为空时返回全部启用地点
        List<CampusPlace> all = campusPlaceMapper.search(null);
        assertTrue(all.size() >= places.size());
        assertTrue(all.stream().allMatch(p -> p.getStatus() == 1));
    }
}

package com.shiro.shiroaiagent.tools;

import cn.hutool.core.util.StrUtil;
import com.shiro.shiroaiagent.service.StudentQueryService;
import com.shiro.shiroaiagent.vo.CreditStatVO;
import com.shiro.shiroaiagent.vo.GraduationProgressVO;
import com.shiro.shiroaiagent.vo.ScoreVO;
import com.shiro.shiroaiagent.vo.StudentProfileVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 学生个人数据查询工具（双通道架构中的"Tool 通道"）
 *
 * 两个关键设计：
 * 身份在请求线程注入，而不是在工具里现取。
 *       Sa-Token 的上下文基于 ThreadLocal，而流式/异步调用链运行在其他线程上，
 *       工具里直接读上下文会拿不到身份。因此本工具是每请求新建的实例，
 *       构造时把已解析好的 studentId 带进来——身份随实例一起进入异步链路。
 *       工具方法没有任何身份参数。模型无法通过"传入学号"去查询他人成绩，
 *       从接口层面杜绝越权。工具描述里也明确写了"只能查询登录学生本人"。
 */
@Slf4j
public class StudentDataTool {

    /** 当前登录学生的 student_id（请求线程解析，随实例带入） */
    private final Long studentId;

    private final StudentQueryService studentQueryService;

    /** 本次请求实际调用过哪些工具（用于回答中的"数据来源"标注与统计） */
    private final List<String> invokedTools = new ArrayList<>();

    public StudentDataTool(Long studentId, StudentQueryService studentQueryService) {
        this.studentId = studentId;
        this.studentQueryService = studentQueryService;
    }

    @Tool(description = """
            查询当前登录学生本人的基本信息与学业概况：学号、姓名、专业、学院、班级、年级、
            平均绩点、已获得学分、毕业要求学分。只能查询登录学生本人，不需要任何参数。
            当用户问"我的绩点是多少""我修了多少学分""我是哪个班的"时使用本工具。""")
    public String queryMyProfile() {
        invokedTools.add("queryMyProfile");
        StudentProfileVO profile = studentQueryService.getProfile(studentId);
        return """
                学号：%s
                姓名：%s
                专业：%s（专业代码 %s，学制 %s 年）
                学院：%s
                班级：%s
                年级：%s
                平均绩点：%s
                已获得学分：%s
                毕业要求总学分：%s（其中必修 %s、选修 %s）
                """.formatted(
                nullToDash(profile.getStudentNo()), nullToDash(profile.getName()),
                nullToDash(profile.getMajorName()), nullToDash(profile.getMajorCode()), nullToDash(profile.getDurationYears()),
                nullToDash(profile.getDeptName()), nullToDash(profile.getClassName()), nullToDash(profile.getGrade()),
                nullToDash(profile.getGpa()), nullToDash(profile.getEarnedCredits()),
                nullToDash(profile.getTotalCredits()), nullToDash(profile.getRequiredCredits()), nullToDash(profile.getElectiveCredits()));
    }

    @Tool(description = """
            查询当前登录学生本人的成绩单。可选参数 courseName 用于按课程名模糊过滤（例如"高等数学"）；
            不传该参数则返回全部课程成绩。同一门课如有补考或重修，会分别列出每一次记录。
            当用户问"我的高数多少分""我挂科了吗""我这学期成绩怎么样""我的某某课过了吗"时使用本工具。
            只能查询登录学生本人，无需也不允许传入学号。""")
    public String queryMyScores(
            @ToolParam(description = "课程名关键词，可选；不传表示查询全部课程") String courseName) {

        invokedTools.add("queryMyScores");
        List<ScoreVO> scores = studentQueryService.getScores(studentId, null);

        if (StrUtil.isNotBlank(courseName)) {
            scores = scores.stream()
                    .filter(s -> s.getCourseName() != null && s.getCourseName().contains(courseName))
                    .collect(Collectors.toList());
            if (scores.isEmpty()) {
                return "没有找到课程名包含「" + courseName + "」的成绩记录。";
            }
        }
        if (scores.isEmpty()) {
            return "该学生暂无成绩记录。";
        }

        StringBuilder sb = new StringBuilder("成绩单（按学期倒序）：\n");
        for (ScoreVO s : scores) {
            sb.append("- ").append(s.getTerm()).append("  ")
                    .append(s.getCourseName())
                    .append("｜学分 ").append(nullToDash(s.getCredit()))
                    .append("｜成绩 ").append(nullToDash(s.getScore()))
                    .append("｜绩点 ").append(nullToDash(s.getGpaPoint()))
                    .append("｜").append(s.getExamType())
                    .append("｜").append(Boolean.TRUE.equals(s.getPassed()) ? "已通过" : "未通过")
                    .append("\n");
        }
        sb.append("说明：同一门课出现「重修」或「补考」记录时，以最新一次通过的成绩为准。\n");
        return sb.toString();
    }

    @Tool(description = """
            查询当前登录学生本人的毕业学分进度：专业要求的必修/选修学分、已获得学分、还差多少学分、
            已通过课程门数。当用户问"我还差多少学分能毕业""我的学分修够了吗""必修课修完了吗"时使用本工具。
            只能查询登录学生本人，不需要任何参数。""")
    public String queryMyProgress() {
        invokedTools.add("queryMyProgress");
        GraduationProgressVO progress = studentQueryService.getProgress(studentId);
        StringBuilder sb = new StringBuilder();
        sb.append("专业：").append(nullToDash(progress.getMajorName())).append("\n")
                .append("毕业要求总学分：").append(nullToDash(progress.getTotalCredits()))
                .append("（必修 ").append(nullToDash(progress.getRequiredCredits()))
                .append("、选修 ").append(nullToDash(progress.getElectiveCredits())).append("）\n")
                .append("已获得总学分：").append(nullToDash(progress.getEarnedCredits())).append("\n")
                .append("已获得必修学分：").append(nullToDash(progress.getEarnedRequiredCredits())).append("\n")
                .append("已获得选修学分：").append(nullToDash(progress.getEarnedElectiveCredits())).append("\n")
                .append("还差学分：").append(nullToDash(progress.getRemainingCredits())).append("\n")
                .append("已通过课程门数：").append(nullToDash(progress.getPassedCourseCount())).append("\n");
        if (progress.getDetail() != null && !progress.getDetail().isEmpty()) {
            sb.append("分类明细：");
            for (CreditStatVO stat : progress.getDetail()) {
                sb.append(stat.getCourseType()).append(" ")
                        .append(nullToDash(stat.getEarnedCredits())).append(" 学分（")
                        .append(nullToDash(stat.getCourseCount())).append(" 门）；");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /** 本次请求是否调用过学生数据工具 */
    public boolean isInvoked() {
        return !invokedTools.isEmpty();
    }

    /** 调用过的工具名列表（用于标注数据来源与后续统计） */
    public List<String> getInvokedTools() {
        return List.copyOf(invokedTools);
    }

    private static String nullToDash(Object value) {
        return value == null || value.toString().isBlank() ? "—" : String.valueOf(value);
    }
}

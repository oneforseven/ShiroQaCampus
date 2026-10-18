package com.shiro.shiroaiagent.controller;

import com.shiro.shiroaiagent.service.StudentQueryService;
import com.shiro.shiroaiagent.vo.GraduationProgressVO;
import com.shiro.shiroaiagent.vo.ScoreVO;
import com.shiro.shiroaiagent.vo.StudentProfileVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 学生本人数据接口
 *
 * <p>注意：所有接口都<b>不带学号参数</b>，只查当前登录学生本人。
 * 这是防越权的关键——前端传不了、模型也编不了别人的学号。</p>
 */
@Tag(name = "学生本人数据", description = "我的成绩、绩点、学分进度（只查当前登录学生本人）")
@RestController
@RequestMapping("/student")
public class StudentQueryController {

    @Resource
    private StudentQueryService studentQueryService;

    @Operation(summary = "我的信息", description = "学号、姓名、专业、学院、平均绩点、已获学分")
    @GetMapping("/me")
    public StudentProfileVO me() {
        return studentQueryService.getMyProfile();
    }

    @Operation(summary = "我的成绩单", description = "可按学期过滤；同一门课有补考/重修会分别列出")
    @GetMapping("/me/scores")
    public List<ScoreVO> myScores(
            @Parameter(description = "学期，如 2023-2024-1；不传则查询全部学期")
            @RequestParam(required = false) String term) {
        return studentQueryService.getMyScores(term);
    }

    @Operation(summary = "我的毕业学分进度", description = "专业要求的必修/选修学分 vs 已获得 vs 还差多少")
    @GetMapping("/me/progress")
    public GraduationProgressVO myProgress() {
        return studentQueryService.getMyProgress();
    }
}

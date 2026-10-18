package com.shiro.shiroaiagent.service;

import com.shiro.shiroaiagent.mapper.ScoreMapper;
import com.shiro.shiroaiagent.mapper.StudentMapper;
import com.shiro.shiroaiagent.security.CurrentUserHolder;
import com.shiro.shiroaiagent.vo.CreditStatVO;
import com.shiro.shiroaiagent.vo.GraduationProgressVO;
import com.shiro.shiroaiagent.vo.ScoreVO;
import com.shiro.shiroaiagent.vo.StudentProfileVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 学生本人数据查询服务（"我的成绩 / 我的绩点 / 我还差多少学分"）
 *
 * <p><b>两套入口：</b></p>
 * <ul>
 *   <li>{@code getMyXxx()}：给 HTTP 接口用，身份从 {@link CurrentUserHolder} 取（登录态）；</li>
 *   <li>{@code getXxx(studentId)}：给 AI 工具用，身份由<b>请求线程解析后显式传入</b>——
 *       因为流式/异步调用链拿不到 Sa-Token 的 ThreadLocal 上下文。</li>
 * </ul>
 * 无论哪套入口都<b>不接受前端或模型传入的学号</b>，从接口层面杜绝越权查询。
 */
@Service
@Slf4j
public class StudentQueryService {

    /** 课程性质：必修 */
    private static final String TYPE_REQUIRED = "必修";

    /** 课程性质：选修 */
    private static final String TYPE_ELECTIVE = "选修";

    @Resource
    private StudentMapper studentMapper;

    @Resource
    private ScoreMapper scoreMapper;

    // ===================== 对外接口用：身份来自登录态 =====================

    /** 我的基本信息（含平均绩点、已获学分、专业与毕业学分要求） */
    public StudentProfileVO getMyProfile() {
        return getProfile(CurrentUserHolder.currentStudentId());
    }

    /** 我的成绩单；term 为空表示全部学期 */
    public List<ScoreVO> getMyScores(String term) {
        return getScores(CurrentUserHolder.currentStudentId(), term);
    }

    /** 我的毕业学分进度 */
    public GraduationProgressVO getMyProgress() {
        return getProgress(CurrentUserHolder.currentStudentId());
    }

    // ===================== 内部用：身份显式传入（AI 工具走这里） =====================

    /**
     * 按 studentId 查询学生信息
     * <p>调用方负责保证 studentId 来自可信的登录身份，不允许来自前端参数或模型生成的参数。</p>
     */
    public StudentProfileVO getProfile(Long studentId) {
        StudentProfileVO profile = studentMapper.selectProfile(studentId);
        if (profile == null) {
            throw new IllegalStateException("未找到学生信息，studentId=" + studentId);
        }
        log.info("查询学生信息：studentId={}, studentNo={}, gpa={}",
                studentId, profile.getStudentNo(), profile.getGpa());
        return profile;
    }

    /**
     * 按 studentId 查询成绩单
     *
     * @param term 学期，如 2026-2027-1；为空则查全部学期
     */
    public List<ScoreVO> getScores(Long studentId, String term) {
        List<ScoreVO> scores = scoreMapper.selectMyScores(studentId, term);
        log.info("查询成绩单：studentId={}, term={}, 共{}条", studentId, term, scores.size());
        return scores;
    }

    /**
     * 按 studentId 查询毕业学分进度
     */
    public GraduationProgressVO getProgress(Long studentId) {
        StudentProfileVO profile = studentMapper.selectProfile(studentId);
        if (profile == null) {
            throw new IllegalStateException("未找到学生信息，studentId=" + studentId);
        }
        List<CreditStatVO> stats = scoreMapper.selectEarnedCreditStats(studentId);

        BigDecimal earnedTotal = BigDecimal.ZERO;
        BigDecimal earnedRequired = BigDecimal.ZERO;
        BigDecimal earnedElective = BigDecimal.ZERO;
        int passedCourseCount = 0;
        for (CreditStatVO stat : stats) {
            BigDecimal credits = stat.getEarnedCredits() == null ? BigDecimal.ZERO : stat.getEarnedCredits();
            earnedTotal = earnedTotal.add(credits);
            if (TYPE_REQUIRED.equals(stat.getCourseType())) {
                earnedRequired = earnedRequired.add(credits);
            } else if (TYPE_ELECTIVE.equals(stat.getCourseType())) {
                earnedElective = earnedElective.add(credits);
            }
            passedCourseCount += stat.getCourseCount() == null ? 0 : stat.getCourseCount();
        }

        GraduationProgressVO vo = new GraduationProgressVO();
        vo.setMajorName(profile.getMajorName());
        vo.setTotalCredits(profile.getTotalCredits());
        vo.setRequiredCredits(profile.getRequiredCredits());
        vo.setElectiveCredits(profile.getElectiveCredits());
        vo.setEarnedCredits(earnedTotal);
        vo.setEarnedRequiredCredits(earnedRequired);
        vo.setEarnedElectiveCredits(earnedElective);
        vo.setPassedCourseCount(passedCourseCount);
        vo.setRemainingCredits(profile.getTotalCredits() == null
                ? null : profile.getTotalCredits().subtract(earnedTotal));
        vo.setDetail(stats);

        log.info("查询毕业学分进度：studentId={}, 已获{}学分, 还差{}学分",
                studentId, earnedTotal, vo.getRemainingCredits());
        return vo;
    }
}

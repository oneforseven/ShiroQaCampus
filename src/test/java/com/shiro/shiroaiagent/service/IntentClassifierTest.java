package com.shiro.shiroaiagent.service;

import com.shiro.shiroaiagent.security.LoginUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 场景意图分类与路由测试（三层策略）
 *
 * 测试环境说明：本测试不注入 ChatModel，因此第 2 层（LLM 分类）会被跳过，
 * 恰好可以验证 第 1 层规则与第 3 层降级的行为。
 * LLM 分类层需要启动应用后通过接口验证。
 *
 * 价值：本测试即实验 E8（场景意图分类）评测问题集的雏形——
 * 每条用例都是一个"问题 → 期望通道/场景"的标注样本，
 * 换用 LLM 分类方案后可用同一套样本对比准确率。
 */
@DisplayName("意图分类：三层策略（规则 / LLM / 降级）")
class IntentClassifierTest {

    private final IntentClassifier classifier = new IntentClassifier();

    private LoginUser student() {
        LoginUser u = new LoginUser();
        u.setUserId(4L);
        u.setStudentId(1L);
        u.setStudentNo("2022001");
        u.setUserType("student");
        return u;
    }

    private LoginUser admin() {
        LoginUser u = new LoginUser();
        u.setUserId(1L);
        u.setUserType("admin");
        return u;
    }

    // ==================== 第 1 层：规则——个人数据 ====================

    @Test
    @DisplayName("第1层：个人数据类问题 → PERSONAL_DATA，且要求学生身份")
    void personalDataByRule() {
        String[] questions = {
                "我的绩点是多少？",
                "我的成绩怎么样",
                "我的高等数学过了吗？",
                "我还差多少学分毕业？",
                "我挂科了吗",
                "我修了多少学分",
                "我的排名是多少"
        };
        for (String q : questions) {
            IntentClassifier.IntentResult r = classifier.classify(q, student());
            assertEquals(IntentClassifier.Channel.PERSONAL_DATA, r.channel(), "问题：" + q);
            assertTrue(r.needsStudentRole(), "问题：" + q + " 应要求学生身份");
            assertFalse(r.byLlm(), "问题：" + q + " 应命中规则层而非 LLM");
        }
    }

    @Test
    @DisplayName("第1层：他人数据请求 → PERSONAL_DATA（供上层拒绝）")
    void othersDataByRule() {
        String[] questions = {
                "查一下2022002的成绩",
                "同学的成绩怎么样",
                "别人的绩点是多少",
                "学号2022002的成绩"
        };
        for (String q : questions) {
            IntentClassifier.IntentResult r = classifier.classify(q, student());
            assertEquals(IntentClassifier.Channel.PERSONAL_DATA, r.channel(), "问题：" + q);
            assertTrue(r.needsStudentRole(), "问题：" + q);
            assertTrue(classifier.isOthersDataRequest(q), "应被识别为他人数据请求：" + q);
        }
    }

    // ==================== 第 1 层：规则——导航 ====================

    @Test
    @DisplayName("第1层：明确的办事/导航问法 → NAVIGATION")
    void navigationByRule() {
        String[] questions = {
                "补办学生证去哪办？",
                "教务处在哪里",
                "图书馆几点上班",
                "就业中心的电话是多少",
                "三方协议去哪个办公室办理",
                "图书馆怎么走"
        };
        for (String q : questions) {
            IntentClassifier.IntentResult r = classifier.classify(q, student());
            assertEquals(IntentClassifier.Channel.NAVIGATION, r.channel(), "问题：" + q);
            assertFalse(r.needsStudentRole(), "问题：" + q + " 不应要求学生身份");
        }
    }

    // ==================== 第 1 层：规则——知识类 ====================

    @Test
    @DisplayName("第1层：命中场景关键词的常规问题 → KNOWLEDGE")
    void knowledgeByRule() {
        String[] questions = {
                "图书馆本科生能借几本书？",
                "一等奖学金的评定条件是什么",
                "转专业需要什么条件",
                "三方协议怎么签",
                "校园卡怎么充值"
        };
        for (String q : questions) {
            IntentClassifier.IntentResult r = classifier.classify(q, student());
            assertEquals(IntentClassifier.Channel.KNOWLEDGE, r.channel(), "问题：" + q);
        }
    }

    // ==================== 第 3 层：降级（模糊问题不硬判） ====================

    @Test
    @DisplayName("第3层：模糊的办事类问题不硬判通道，降级为 AMBIGUOUS（交给模型自行判断）")
    void ambiguousQuestionsDegradeGracefully() {
        // 这些问题的语义是"办事"，但问法不含明确导航词——
        // 规则层故意不判，避免错误路由；测试环境无 ChatModel，故落到第 3 层
        String[] questions = {
                "我学生证丢了怎么办",
                "学生证补办流程是什么",
                "补办学生证需要什么材料",
                "校园卡丢了要带什么材料",
        };
        for (String q : questions) {
            IntentClassifier.IntentResult r = classifier.classify(q, student());
            assertEquals(IntentClassifier.Channel.AMBIGUOUS, r.channel(),
                    "问题：" + q + " 应降级为 AMBIGUOUS 而不是被硬判");
            assertFalse(r.needsStudentRole(),
                    "问题：" + q + " 不应触发学生身份拦截（否则学生会被误拦）");
            assertFalse(r.byLlm());
        }
    }

    @Test
    @DisplayName("第3层：无法识别的问法 → AMBIGUOUS，空问题也不抛异常")
    void unknownAndBlankQuestions() {
        IntentClassifier.IntentResult unknown = classifier.classify("今天天气不错", student());
        assertEquals(IntentClassifier.Channel.AMBIGUOUS, unknown.channel());

        IntentClassifier.IntentResult blank = classifier.classify("", student());
        assertEquals(IntentClassifier.Channel.AMBIGUOUS, blank.channel());
        assertEquals(IntentClassifier.Scene.OTHER, blank.scene());
    }

    // ==================== 场景识别（用于统计） ====================

    @Test
    @DisplayName("六大场景识别正确")
    void sceneClassification() {
        assertEquals(IntentClassifier.Scene.TS, classifier.classify("图书馆能借几本书", student()).scene());
        assertEquals(IntentClassifier.Scene.JY, classifier.classify("三方协议怎么签", student()).scene());
        assertEquals(IntentClassifier.Scene.XS, classifier.classify("奖学金怎么申请", student()).scene());
        assertEquals(IntentClassifier.Scene.JW, classifier.classify("转专业的条件是什么", student()).scene());
        assertEquals(IntentClassifier.Scene.SH, classifier.classify("宿舍几点关门", student()).scene());
        assertEquals(IntentClassifier.Scene.DH, classifier.classify("补办学生证去哪办", student()).scene());
    }

    @Test
    @DisplayName("宿舍类问题不会因排序问题被误判为导航类（回归测试）")
    void dormitoryNotMisclassifiedAsNavigation() {
        IntentClassifier.IntentResult r = classifier.classify("宿舍几点关门", student());
        assertEquals(IntentClassifier.Scene.SH, r.scene(), "宿舍属于校园生活，不应被判为导航");
        assertEquals(IntentClassifier.Channel.KNOWLEDGE, r.channel());
    }

    // ==================== 权限相关 ====================

    @Test
    @DisplayName("管理员问个人数据：规则层同样要求身份，上层据此拦截")
    void adminAskingPersonalData() {
        IntentClassifier.IntentResult r = classifier.classify("我的绩点是多少？", admin());
        assertEquals(IntentClassifier.Channel.PERSONAL_DATA, r.channel());
        assertTrue(r.needsStudentRole(), "管理员无 studentId，应被权限前置拦截");
    }
}

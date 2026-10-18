package com.shiro.shiroaiagent.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.shiro.shiroaiagent.security.LoginUser;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 场景意图分类与路由（三层策略）
 *
 * 为什么是三层而不是纯关键词：用户的表达本身是模糊的。
 * 同一个"补办学生证"的需求，可能被问成"去哪办"（明确导航）、"我学生证丢了怎么办"（模糊）、
 * "补办流程是什么"（还可能是问规定）。纯关键词穷举永远补不全，
 * 而纯 LLM 分类每次都要多一次模型调用（慢且贵）。因此采用分层：
 *
 * 1- 高置信度规则：命中明确特征就直接判定，零成本、结果稳定，
 *    覆盖最常见的问法（占绝大多数请求）；
 * 2- LLM 分类兜底：规则不确定时（如含"补办/丢了/流程/材料"等模糊词），
 *    调一次轻量分类，让模型理解语义；
 * 3- 意图不明确：LLM 也不可用或分类失败时，返回 {@link Channel#AMBIGUOUS}，
 *    由上层把全部工具交给模型自行判断——宁可多给工具，也不要错误路由。
 *
 *
 */
@Service
@Slf4j
public class IntentClassifier {

    /** 六大场景 */
    public enum Scene {
        JW("教务问答"), XS("学生事务"), SH("校园生活"), TS("图书馆服务"), JY("就业服务"), DH("校园导航"), OTHER("其他");

        private final String label;

        Scene(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        /** 从中文标签解析场景（LLM 返回的是标签） */
        public static Scene fromLabel(String label) {
            if (label == null) {
                return OTHER;
            }
            for (Scene s : values()) {
                if (s.label.equals(label.trim())) {
                    return s;
                }
            }
            return OTHER;
        }
    }

    /** 问题需要走哪条通道 */
    public enum Channel {
        /** 个人数据类：必须查业务库，且需要学生身份 */
        PERSONAL_DATA("PERSONAL_DATA"),
        /** 校园导航/办事类：查结构化地点数据，也常需结合规定说明 */
        NAVIGATION("NAVIGATION"),
        /** 知识类：走知识库检索 */
        KNOWLEDGE("KNOWLEDGE"),
        /** 意图不明确：交给模型自行判断（给全部工具） */
        AMBIGUOUS("AMBIGUOUS");

        private final String code;

        Channel(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        public static Channel fromCode(String code) {
            if (code == null) {
                return null;
            }
            for (Channel c : values()) {
                if (c.code.equalsIgnoreCase(code.trim())) {
                    return c;
                }
            }
            return null;
        }
    }

    /**
     * 分类结果
     *
     * @param channel          通道判定
     * @param scene            场景（用于统计）
     * @param needsStudentRole 是否要求学生身份（个人数据类为 true）
     * @param reason           判定依据（便于调试与统计）
     * @param byLlm            是否由 LLM 判定（用于 E8 实验统计两类方案的占比）
     */
    public record IntentResult(Channel channel, Scene scene, boolean needsStudentRole,
                               String reason, boolean byLlm) {
    }

    /** LLM 分类器（字段注入，便于在纯单元测试里不注入 ChatModel 也能运行） */
    @Resource
    private ChatModel dashscopeChatModel;

    // ==================== 第 1 层：高置信度规则 ====================

    /** 强烈指向个人数据的词（出现即判定） */
    private static final List<String> PERSONAL_STRONG = List.of(
            "我的绩点", "我的成绩", "我的学分", "我的分数", "我的GPA", "我的gpa",
            "我绩点", "我成绩", "我学分", "我挂科", "我过了吗", "我还差", "我修了",
            "我修了多少", "我的排名", "我的课表", "我考了多少", "我未通过", "我及格");

    /**
     * 个人数据泛匹配
     *
     * <p>包含口语化问法的兜底（"我过了吗""我高数多少分"）——这类表达不带"成绩/绩点"字样，
     * 但语义上就是在问个人数据。规则层不可能穷举所有说法，
     * 漏掉的会落到第 2 层 LLM 分类，这里只是尽量覆盖高频表达。</p>
     */
    private static final Pattern PERSONAL_PATTERN = Pattern.compile(
            "我的.*(绩点|成绩|学分|分数|排名|课表|选课|挂科|重修|补考)|"
                    + "我.{0,10}(过了吗|过没过|及格了吗|挂了吗|通过了吗|多少分|几分)|"
                    + "我.*(绩点|成绩|学分).*(多少|几|怎么样|如何)");

    /** 他人数据请求 */
    private static final Pattern OTHERS_DATA_PATTERN = Pattern.compile(
            "查一下.*(成绩|绩点|学分)|.*同学.*(成绩|绩点)|别人.*(成绩|绩点)|"
                    + "学号\\s*\\d+.*(成绩|绩点)|\\d{7,}.*(成绩|绩点|多少分)");

    /** 明确的导航/办事问法（高置信度） */
    private static final List<String> NAV_STRONG = List.of(
            "去哪", "去哪里", "在哪儿", "在哪里", "在哪个楼", "哪个楼", "哪个办公室", "哪个部门",
            "怎么走", "几点上班", "几点办公", "办公时间", "上班时间", "咨询电话", "联系电话",
            "电话是多少", "电话多少", "办理地点", "在哪办", "去哪个", "找谁", "什么位置", "地址在哪");

    /**
     * 模糊的"办事意图"信号词 —— 出现这些词时<b>不直接判定</b>，转第 2 层 LLM 分类。
     *
     * <p>因为这些词既可能是在问"怎么办手续"（导航），也可能是在问"规定是什么"（知识），
     * 例如"补办学生证怎么办"（办事）与"转专业需要什么条件"（规定）。
     * 规则在这里无法可靠区分，交给模型理解语义更稳妥。</p>
     */
    private static final List<String> AMBIGUOUS_HINTS = List.of(
            "补办", "丢了", "遗失", "怎么办", "如何办理", "怎么办理", "办理流程", "流程",
            "手续", "材料", "需要带", "要带什么", "重新办", "换证", "申领");

    /** 场景关键词（按顺序匹配；DH 无关，导航通道由 NAV_STRONG / LLM 判定） */
    private static final List<SceneKeyword> SCENE_KEYWORDS = List.of(
            new SceneKeyword(Scene.TS, List.of("图书馆", "借书", "还书", "续借", "借阅", "数据库使用", "文献", "查重", "自习")),
            new SceneKeyword(Scene.JY, List.of("就业", "招聘", "三方协议", "报到证", "档案", "简历", "实习", "面试", "毕业去向", "校招")),
            new SceneKeyword(Scene.XS, List.of("奖学金", "助学金", "评优", "评奖", "请假", "学籍异动", "助学贷款", "勤工助学", "困难认定")),
            new SceneKeyword(Scene.SH, List.of("宿舍", "食堂", "校园卡", "校车", "网络服务", "校园网", "门禁", "报修", "后勤", "上网账号")),
            new SceneKeyword(Scene.JW, List.of("选课", "考试", "成绩", "学分", "转专业", "毕业", "培养方案", "学籍", "补考", "重修", "缓考", "绩点", "课程", "学生证")),
            new SceneKeyword(Scene.DH, List.of("办事", "窗口", "服务中心", "行政楼", "主楼", "教务处"))
    );

    private record SceneKeyword(Scene scene, List<String> keywords) {
    }

    // ==================== 对外入口 ====================

    /**
     * 对问题进行分类（三层策略）
     */
    public IntentResult classify(String question, LoginUser loginUser) {
        String q = StrUtil.blankToDefault(question, "").trim();
        if (StrUtil.isBlank(q)) {
            return new IntentResult(Channel.AMBIGUOUS, Scene.OTHER, false, "空问题", false);
        }

        // ---------- 第 1 层：高置信度规则 ----------
        IntentResult ruleResult = classifyByRules(q);
        if (ruleResult != null) {
            log.info("意图分类[规则]：通道={}, 场景={}, reason={}, question={}",
                    ruleResult.channel(), ruleResult.scene().label(), ruleResult.reason(), q);
            return ruleResult;
        }

        // ---------- 第 2 层：LLM 兜底 ----------
        IntentResult llmResult = classifyByLlm(q);
        if (llmResult != null) {
            log.info("意图分类[LLM]：通道={}, 场景={}, question={}",
                    llmResult.channel(), llmResult.scene().label(), q);
            return llmResult;
        }

        // ---------- 第 3 层：意图不明确（交给模型自行判断，给全部工具）----------
        log.info("意图分类[不确定]：返回 AMBIGUOUS，交由模型自行判断, question={}", q);
        return new IntentResult(Channel.AMBIGUOUS, matchScene(q), false,
                "规则与模型均未给出明确判定", false);
    }

    // ==================== 第 1 层实现 ====================

    /**
     * 规则分类：只在<b>有把握</b>时返回结果，否则返回 null 交给下一层
     */
    private IntentResult classifyByRules(String q) {
        // 1) 他人数据 → 个人数据类（由上层拒绝）
        if (OTHERS_DATA_PATTERN.matcher(q).find()) {
            return new IntentResult(Channel.PERSONAL_DATA, matchScene(q), true,
                    "规则：检测到他人数据请求", false);
        }

        // 2) 个人数据 → 强特征
        if (PERSONAL_STRONG.stream().anyMatch(q::contains)
                || PERSONAL_PATTERN.matcher(q).find()) {
            return new IntentResult(Channel.PERSONAL_DATA, matchScene(q), true,
                    "规则：命中个人数据强特征", false);
        }

        // 3) 明确的导航问法
        if (NAV_STRONG.stream().anyMatch(q::contains)) {
            return new IntentResult(Channel.NAVIGATION, Scene.DH, false,
                    "规则：命中明确办事/导航问法", false);
        }

        // 4) 模糊办事信号 → 不判定，转 LLM
        if (AMBIGUOUS_HINTS.stream().anyMatch(q::contains)) {
            return null;
        }

        // 5) 命中场景关键词且无明显个人/导航特征 → 知识类
        Scene scene = matchScene(q);
        if (scene != Scene.OTHER) {
            return new IntentResult(Channel.KNOWLEDGE, scene, false,
                    "规则：命中场景关键词「" + scene.label() + "」", false);
        }

        // 6) 其余情况无法可靠判定（如"学校有什么规定"）→ 转 LLM
        return null;
    }

    // ==================== 第 2 层实现 ====================

    /**
     * 用 LLM 做意图分类；不可用或解析失败时返回 null（降级到第 3 层）
     */
    private IntentResult classifyByLlm(String question) {
        if (dashscopeChatModel == null) {
            // 纯单元测试环境下没有 ChatModel，直接跳过
            return null;
        }
        try {
            String prompt = """
                    你是校园问答系统的意图分类器。请判断下面这个用户问题应该走哪条处理通道。
                    只输出一个 JSON 对象，不要输出任何解释、不要加代码块标记。

                    通道定义：
                    - PERSONAL_DATA：询问【本人的】成绩、绩点、学分、排名、课表等个人数据
                    - NAVIGATION：询问办事地点、办公时间、联系电话，或某项手续【怎么办、需要什么材料、走什么流程】
                    - KNOWLEDGE：询问规章制度、评定条件、标准、规定的内容

                    场景只能取以下之一：教务问答 / 学生事务 / 校园生活 / 图书馆服务 / 就业服务 / 校园导航 / 其他

                    输出格式示例：{"channel":"NAVIGATION","scene":"教务问答"}

                    用户问题：%s
                    """.formatted(question);

            String raw = ChatClient.create(dashscopeChatModel)
                    .prompt()
                    .user(prompt)
                    .call()
                    .chatResponse()
                    .getResult()
                    .getOutput()
                    .getText();

            String json = extractJson(raw);
            if (json == null) {
                log.warn("LLM 意图分类返回内容无法解析为 JSON：{}", raw);
                return null;
            }
            JSONObject obj = JSONUtil.parseObj(json);
            Channel channel = Channel.fromCode(obj.getStr("channel"));
            if (channel == null) {
                log.warn("LLM 意图分类返回未知通道：{}", json);
                return null;
            }
            Scene scene = Scene.fromLabel(obj.getStr("scene"));
            boolean needsStudentRole = channel == Channel.PERSONAL_DATA;
            return new IntentResult(channel, scene, needsStudentRole, "LLM 语义分类", true);
        } catch (Exception e) {
            log.warn("LLM 意图分类失败，降级为 AMBIGUOUS：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 从模型输出里抠出第一个 JSON 对象（模型有时会加 ```json 或多余说明）
     */
    private String extractJson(String raw) {
        if (StrUtil.isBlank(raw)) {
            return null;
        }
        Matcher m = Pattern.compile("\\{[^{}]*}").matcher(raw);
        return m.find() ? m.group() : null;
    }

    // ==================== 场景识别（供统计使用） ====================

    private Scene matchScene(String q) {
        for (SceneKeyword sk : SCENE_KEYWORDS) {
            for (String kw : sk.keywords()) {
                if (q.contains(kw)) {
                    return sk.scene();
                }
            }
        }
        return Scene.OTHER;
    }

    /**
     * 供上层判断"他人数据请求"（与规则层保持一致）
     */
    public boolean isOthersDataRequest(String question) {
        return OTHERS_DATA_PATTERN.matcher(StrUtil.blankToDefault(question, "")).find();
    }

    /**
     * "流程/材料"类追问信号
     *
     * <p><b>用途：</b>导航类问题默认跳过知识库检索（避免无关片段干扰），
     * 但如果用户同时问"怎么办、流程、需要什么材料"，就<b>必须补上知识库检索</b>——
     * 否则地点/时间由工具给出，而流程细节只能由模型凭常识补全，<b>这正是幻觉的来源</b>。</p>
     */
    private static final List<String> PROCEDURE_HINTS = List.of(
            "怎么办", "如何办理", "怎么办理", "怎么申请", "如何申请", "办理流程", "流程",
            "手续", "材料", "需要带", "要带什么", "步骤", "有哪些要求", "需要哪些");

    /**
     * 问题是否包含"流程/材料"类追问
     */
    public boolean hasProcedureHint(String question) {
        String q = StrUtil.blankToDefault(question, "");
        return PROCEDURE_HINTS.stream().anyMatch(q::contains);
    }
}

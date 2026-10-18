package com.shiro.shiroaiagent.app;

import cn.hutool.core.util.StrUtil;
import com.shiro.shiroaiagent.advisor.MyLoggerAdvisor;
import com.shiro.shiroaiagent.chatmemory.FileBasedChatMemory;
import com.shiro.shiroaiagent.security.LoginUser;
import com.shiro.shiroaiagent.service.CampusPlaceService;
import com.shiro.shiroaiagent.service.IntentClassifier;
import com.shiro.shiroaiagent.service.StudentQueryService;
import com.shiro.shiroaiagent.tools.CampusPlaceTool;
import com.shiro.shiroaiagent.tools.StudentDataTool;
import com.shiro.shiroaiagent.vo.CampusChatResponseVO;
import com.shiro.shiroaiagent.vo.CitationVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY;

/**
 * 校园智能问答应用（双通道混合问答架构）
 */
@Component
@Slf4j
public class CampusQaApp {

    private static final String SYSTEM_PROMPT = """
            你是本校的校园智能问答助手，服务对象是在校学生，可回答六类问题：
            教务问答（选课、考试、成绩、学分、转专业、毕业要求）、学生事务（奖助学金、评优评奖、请假、学籍异动）、
            校园生活（宿舍、食堂、校园卡、校车、网络服务）、图书馆服务（借阅规则、开放时间、数据库使用）、
            就业服务（招聘活动、就业手续、档案与三方协议）、校园导航（办事地点、办公时间、联系方式）。

            回答规则（必须严格遵守）：
            1. 涉及学校规定、制度、流程、办事要求的问题：只依据【知识库检索结果】回答，不得编造、不得凭印象补充。
               若本次未检索到相关内容，必须明确说明"知识库中暂时没有相关说明"，并建议咨询对应部门。
            2. 涉及"我的成绩、我的绩点、我的学分、我还差多少学分毕业"等个人数据：必须先调用工具查询再回答，
               严禁任何推测或编造数字；工具没返回的数据就说没有查到。
            3. 涉及"去哪办、几点办公、电话多少"：必须调用办事地点查询工具，不得凭印象回答。
            4. 若用户想查询他人的成绩或信息：拒绝，并说明系统只能查询本人数据。
            5. 【严禁编造查询过程】不要输出"正在为你查询""正在读取数据"这类过程性描述。
               如果当前没有可用的查询工具，或工具没有返回数据，就如实说明"无法查询到"，
               绝对不允许凭印象给出一串数字（分数、绩点、学分、电话）——宁可不答，也不能编造。
            6. 回答最后另起一行，用"依据："标注来源，例如：依据：《本科生学籍管理规定》；
               或：依据：教务数据库（本人成绩）。若本次没有查到任何依据，写"依据：无（知识库中暂无相关内容）"。
            7. 语言简洁自然，不要输出工具名、函数名、参数等实现细节。
            """;

    private static final String NO_HIT_BLOCK =
            "\n\n【知识库检索结果】\n（本次未检索到与问题相关的知识库内容，涉及校规校纪类问题请明确告知用户知识库暂无相关说明）";

    @Resource
    private VectorStore pgVectorStore;

    @Resource
    private StudentQueryService studentQueryService;

    @Resource
    private CampusPlaceService campusPlaceService;

    /** 场景意图分类与路由（含权限前置判定） */
    @Resource
    private IntentClassifier intentClassifier;

    /** 问答结果缓存（Redis） */
    @Resource
    private com.shiro.shiroaiagent.service.QaCacheService qaCacheService;

    /** 检索返回条数，抽成配置是为了支撑 E3 对比实验 */
    @Value("${campus.qa.top-k:4}")
    private int topK;

    /** 相似度阈值，抽成配置是为了支撑 E3 对比实验 */
    @Value("${campus.qa.similarity-threshold:0.5}")
    private double similarityThreshold;

    /** 只检索校园语料（docType 元数据），把历史示例文档隔离在外 */
    @Value("${campus.qa.doc-type:campus}")
    private String campusDocType;

    private final ChatClient chatClient;

    public CampusQaApp(ChatModel dashscopeChatModel) {
        String fileDir = System.getProperty("user.dir") + "/tmp/chat-memory";
        ChatMemory chatMemory = new FileBasedChatMemory(fileDir);
        this.chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(
                        new MessageChatMemoryAdvisor(chatMemory),
                        new MyLoggerAdvisor()
                )
                .build();
    }

    /**
     * 双通道问答
     *
     * @param question 用户问题
     * @param chatId   会话 id（前端传入）
     * @param loginUser 当前登录用户（<b>必须在请求线程解析好再传进来</b>）
     */
    public CampusChatResponseVO answer(String question, String chatId, LoginUser loginUser) {
        long start = System.currentTimeMillis();
        Long studentId = loginUser == null ? null : loginUser.getStudentId();
        Long userId = loginUser == null ? null : loginUser.getUserId();

        // ================= 第 0 步：意图分类（三层）+ 权限前置拦截 =================
        // 关键：权限不足时【根本不调用模型】，直接返回明确答复。
        // 这样就不存在"模型查不到数据、于是编一个答案"的幻觉空间。
        IntentClassifier.IntentResult intent = intentClassifier.classify(question, loginUser);
        if (intent.needsStudentRole() && studentId == null) {
            log.warn("个人数据请求被拦截：当前账号无学生身份, userId={}, question={}", userId, question);
            return rejectNoStudentRole(question, intent, start);
        }
        // 他人数据请求：无论什么身份都拒绝
        if (intentClassifier.isOthersDataRequest(question)) {
            log.warn("他人数据请求被拦截：userId={}, question={}", userId, question);
            return rejectOthersData(question, intent, start);
        }

        // ================= 缓存查询（必须在权限校验之后） =================
        // 顺序很关键：先做权限判定，再查缓存。
        // 否则无权用户可能通过缓存拿到本不该看到的内容（缓存绕过权限）。
        CampusChatResponseVO cached = qaCacheService.get(userId, question);
        if (cached != null) {
            cached.setLatencyMs(System.currentTimeMillis() - start);
            return cached;
        }

        // ================= 通道一：知识库检索 =================
        // 检索策略（重要取舍）：
        //   - 纯导航类问题（只问地点/时间/电话）跳过检索，避免无关片段干扰；
        //   - 但只要带"怎么办/流程/材料"这类追问，就【必须检索】——
        //     否则流程细节只能由模型凭常识补全，而这是幻觉的来源（实测踩过）。
        //   - 意图不明确时也检索（模糊问题可能涉及规定性内容）。
        boolean needKnowledge = intent.channel() != IntentClassifier.Channel.NAVIGATION
                || intentClassifier.hasProcedureHint(question);
        List<Document> docs = needKnowledge ? retrieveKnowledge(question) : List.of();

        // 提示词 = 基础规则 + 本次路由提示 + 知识库上下文（或未命中提示）
        String systemPrompt = SYSTEM_PROMPT
                + buildRoutingHint(intent, studentId)
                + (docs.isEmpty() ? NO_HIT_BLOCK : "\n\n【知识库检索结果】\n" + buildContext(docs));

        // ================= 通道二：结构化数据工具 =================
        // 工具实例每请求新建，身份随实例注入（异步线程里读不到 Sa-Token 上下文）
        CampusPlaceTool placeTool = new CampusPlaceTool(campusPlaceService);
        StudentDataTool studentTool = new StudentDataTool(studentId, studentQueryService);

        // 策略：办事地点工具始终可用；个人数据工具仅在学生身份下注入。
        // 意图不明确（AMBIGUOUS）时两者都给，让模型自行判断——宁可多给工具，也不要错误路由。
        List<Object> toolObjects = new ArrayList<>();
        toolObjects.add(placeTool);
        if (studentId != null) {
            toolObjects.add(studentTool);
        }
        ToolCallback[] tools = ToolCallbacks.from(toolObjects.toArray());

        // 会话记忆按用户隔离，避免不同用户共用同一 chatId 时串上下文
        String memoryChatId = "u" + (userId == null ? "anon" : userId)
                + "-" + StrUtil.blankToDefault(chatId, "default");

        String answerText;
        try {
            ChatResponse response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(question)
                    .advisors(spec -> spec
                            .param(CHAT_MEMORY_CONVERSATION_ID_KEY, memoryChatId)
                            .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                    .tools(tools)
                    .call()
                    .chatResponse();
            answerText = response.getResult().getOutput().getText();
        } catch (Exception e) {
            log.error("双通道问答调用失败：question={}", question, e);
            answerText = "抱歉，处理你的问题时出现了异常，请稍后重试。";
        }

        CampusChatResponseVO result = buildResponse(answerText, docs, studentTool, placeTool, start);

        // ================= 写入缓存 =================
        // 个人数据类问题 TTL 更短（成绩可能被管理员修改），知识类问题 TTL 较长。
        qaCacheService.put(userId, question, result, intent.channel() == IntentClassifier.Channel.PERSONAL_DATA);

        return result;
    }

    /**
     * 权限不足：非学生账号请求个人数据 —— 直接拒绝，不进模型
     */
    private CampusChatResponseVO rejectNoStudentRole(String question,
                                                     IntentClassifier.IntentResult intent, long start) {
        return buildRejection(
                "抱歉，当前登录账号不是学生账号，无法查询学生的个人成绩、绩点与学分信息。"
                        + "如需查询个人数据，请使用学生账号登录。",
                intent, start);
    }

    /**
     * 越权请求：查询他人数据 —— 直接拒绝，不进模型
     */
    private CampusChatResponseVO rejectOthersData(String question,
                                                  IntentClassifier.IntentResult intent, long start) {
        return buildRejection(
                "抱歉，系统只能查询您本人的成绩与学业数据，不支持查询其他同学的信息。"
                        + "如需了解他人的学业情况，请通过正规渠道向教务部门申请。",
                intent, start);
    }

    /**
     * 构造统一的"拒绝"响应（关键：不经过大模型，杜绝幻觉与编造）
     */
    private CampusChatResponseVO buildRejection(String answer,
                                                IntentClassifier.IntentResult intent, long start) {
        CampusChatResponseVO vo = new CampusChatResponseVO();
        vo.setAnswer(answer);
        vo.setChannel("拒绝（权限拦截）");
        vo.setHitKnowledgeBase(false);
        vo.setUsedDatabase(false);
        vo.setKbHitCount(0);
        vo.setSources(List.of());
        vo.setLatencyMs(System.currentTimeMillis() - start);
        log.info("问答被拦截并直接拒绝：channel={}, latency={}ms", vo.getChannel(), vo.getLatencyMs());
        return vo;
    }

    /**
     * 生成本次请求的"路由提示"，把意图分类结果翻译成对模型的明确指令
     *
     * <p>作用：让模型知道"该走哪条通道"，避免它在两条通道间摇摆；
     * 对意图不明确的请求，则把判断权交还模型（并给出判断依据）。</p>
     */
    private String buildRoutingHint(IntentClassifier.IntentResult intent, Long studentId) {
        StringBuilder sb = new StringBuilder("\n\n【本次请求的路由提示】\n");
        switch (intent.channel()) {
            case PERSONAL_DATA -> sb.append("""
                    已判定为【个人数据类】问题：必须先调用个人数据查询工具获取真实数据再回答，
                    严禁凭推测或印象作答。工具返回什么就说什么。
                    """);
            case NAVIGATION -> sb.append("""
                    已判定为【办事/导航类】问题：请优先调用办事地点查询工具，获取办理地点、办公时间与联系电话；
                    若用户还问"怎么办、需要什么材料、走什么流程"，可结合知识库检索结果一并说明。
                    """);
            case KNOWLEDGE -> sb.append("""
                    已判定为【知识/规定类】问题：请依据【知识库检索结果】回答；
                    若未检索到相关内容，如实说明知识库暂无相关说明，并建议咨询对应部门。
                    """);
            case AMBIGUOUS -> {
                sb.append("""
                        本问题意图不明确，请你自行判断后再作答：
                        - 涉及本人成绩、绩点、学分等个人数据 → 调用个人数据查询工具；
                        - 涉及办事地点、办公时间、联系电话、办理手续 → 调用办事地点查询工具；
                        - 涉及规章制度、评定条件、标准 → 依据【知识库检索结果】；
                        - 若三类都不涉及，就如实说明无法回答，不要编造。
                        """);
                if (studentId == null) {
                    sb.append("注意：当前登录账号不是学生账号，涉及个人数据的请求请直接说明无法查询，不要编造任何数字。\n");
                }
            }
        }
        return sb.toString();
    }

    /**
     * 通道一：向量检索（带元数据过滤，只取校园语料）
     */
    private List<Document> retrieveKnowledge(String question) {
        try {
            SearchRequest request = SearchRequest.builder()
                    .query(question)
                    .topK(topK)
                    .similarityThreshold(similarityThreshold)
                    // 只检索校园语料；后续部门权限过滤也复用这里的 metadata filter 机制
                    .filterExpression("docType == '" + campusDocType + "'")
                    .build();
            List<Document> docs = pgVectorStore.similaritySearch(request);
            log.info("知识库检索：question={}, topK={}, threshold={}, 命中{}条",
                    question, topK, similarityThreshold, docs == null ? 0 : docs.size());
            return docs == null ? List.of() : docs;
        } catch (Exception e) {
            log.error("知识库检索失败，降级为无知识库上下文继续作答", e);
            return List.of();
        }
    }

    private String buildContext(List<Document> docs) {
        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (Document doc : docs) {
            sb.append("【片段").append(index++).append("】来源：")
                    .append(docTitle(doc))
                    .append("\n")
                    .append(doc.getText())
                    .append("\n\n");
        }
        return sb.toString();
    }

    private CampusChatResponseVO buildResponse(String answerText, List<Document> docs,
                                               StudentDataTool studentTool, CampusPlaceTool placeTool,
                                               long start) {
        boolean kbHit = !docs.isEmpty();

        // ★ 来源标注只依据"确实被调用过的工具"，避免出现虚假的来源说明。
        //   StudentDataTool 在非学生账号下不会被注入，因此它一旦被调用，必然是真查了本人数据。
        boolean studentToolUsed = studentTool.isInvoked();
        boolean placeToolUsed = placeTool.isInvoked();
        boolean dbUsed = studentToolUsed || placeToolUsed;

        List<CitationVO> sources = new ArrayList<>();
        for (Document doc : docs) {
            sources.add(new CitationVO("知识库", docTitle(doc), snippet(doc.getText())));
        }
        if (studentToolUsed) {
            sources.add(new CitationVO("数据库", "教务数据库（本人学籍与成绩）",
                    "实时查询：" + String.join("、", studentTool.getInvokedTools())));
        }
        if (placeToolUsed) {
            sources.add(new CitationVO("数据库", "校园办事地点数据", "实时查询：办事地点与办公时间"));
        }

        CampusChatResponseVO vo = new CampusChatResponseVO();
        vo.setAnswer(answerText);
        vo.setHitKnowledgeBase(kbHit);
        vo.setUsedDatabase(dbUsed);
        vo.setKbHitCount(docs.size());
        vo.setSources(sources);
        vo.setChannel(kbHit && dbUsed ? "RAG+Tool" : kbHit ? "RAG" : dbUsed ? "Tool" : "无依据");
        vo.setLatencyMs(System.currentTimeMillis() - start);
        log.info("双通道问答完成：channel={}, kbHit={}, dbUsed={}(学生工具={}, 导航工具={}), latency={}ms",
                vo.getChannel(), kbHit, dbUsed, studentToolUsed, placeToolUsed, vo.getLatencyMs());
        return vo;
    }

    /** 引用标题：优先用文档标题元数据，退回文件名 */
    private String docTitle(Document doc) {
        Object title = doc.getMetadata().get("docTitle");
        if (title != null) {
            return String.valueOf(title);
        }
        Object filename = doc.getMetadata().get("filename");
        if (filename != null) {
            return String.valueOf(filename);
        }
        return "知识库片段";
    }

    private String snippet(String text) {
        if (text == null) {
            return "";
        }
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() <= 80 ? flat : flat.substring(0, 80) + "...";
    }
}

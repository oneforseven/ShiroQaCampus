package com.shiro.shiroaiagent.controller;


import com.shiro.shiroaiagent.agent.ShiroManus;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 智能体接口（工具调用过程可视化）
 *
 * <p><b>说明：</b>校园问答的主入口在 {@code CampusQaController}（{@code POST /ai/campus/chat}），
 * 本控制器只保留 {@link ShiroManus} 智能体——它会把「思考 → 调用工具 → 观察结果」
 * 的每一步实时推给前端，用于演示系统的工具调用与执行过程可视化能力。</p>
 */
@RestController
@RequestMapping("/ai")
public class AIController {

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ChatModel dashscopeChatModel;

    /**
     * 流式调用 Manus 智能体：逐步推送执行过程（模型说明 + 工具调用结果）
     *
     * @param message 用户指令
     * @return SSE 流
     */
    @GetMapping("/manus/chat")
    public SseEmitter doChatWithManus(String message) {
        ShiroManus shiroManus = new ShiroManus(allTools, dashscopeChatModel);
        return shiroManus.runStream(message);
    }
}

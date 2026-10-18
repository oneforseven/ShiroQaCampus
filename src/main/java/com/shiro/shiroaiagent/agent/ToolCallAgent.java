package com.shiro.shiroaiagent.agent;


import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.shiro.shiroaiagent.agent.model.AgentState;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 处理工具调用的基础代理类，具体实现了 think 和 act 方法，可以用创建实例的父类
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class ToolCallAgent extends ReActAgent{

    // 可用的工具
    private final ToolCallback[] availableTools;

    // 保存工具调用信息的响应结果（要调用哪些工具）
    private ChatResponse toolCallChatResponse;

    // 模型每一步的说明文字（用于流式推送给前端）
    private String lastThinkText = "";

    // 工具调用管理者
    private final ToolCallingManager toolCallingManager;

    private final ChatOptions chatOptions;

    /** 连续失败达到该次数则任务终止，避免空转跑满 maxSteps */
    private int consecutiveErrorCount = 0;
    private static final int MAX_CONSECUTIVE_ERRORS = 3;

    /**
     * 当前任务已经执行到第几步。
     * 注意：不要用 BaseAgent.currentStep —— 它在 run() 里是循环内的局部变量，属性永远是初始值 0。
     */
    private int stepCount = 0;

    /** 完整行动指令（nextStepPrompt）是否已经注入。每次任务开始重置，保证每个任务只注入一次。 */
    private boolean nextStepPromptInjected = false;

    public ToolCallAgent(ToolCallback[] availableTools) {
        super();
        this.availableTools = availableTools;
        this.toolCallingManager = ToolCallingManager.builder().build();
        // 禁用 Spring AI 内置的工具调用机制，自己维护选项和消息上下文 （阿里云与Spring AI 大模型调用方式不兼容）
        this.chatOptions = DashScopeChatOptions.builder()
                .withProxyToolCalls(true)
                .build();
    }

    /**
     * 处理当前状态并决定下一步行动
     *
     * @return 是否需要执行行动
     */
    @Override
    public boolean think() {
        // 1. 第 1 步把完整的行动指令注入上下文；之后不再重复注入，避免同一段规则每步累积一次
        stepCount++;
        if (!isNextStepPromptInjected() && StrUtil.isNotBlank(getNextStepPrompt())) {
            getMessageList().add(new UserMessage(getNextStepPrompt()));
            setNextStepPromptInjected(true);
        }
        List<Message> messageList = getMessageList();
        Prompt prompt = new Prompt(messageList, this.chatOptions);

        // 2.调用 AI 模型，获取工具调用结果
        try {
            ChatResponse chatResponse = getChatClient().prompt(prompt)
                    .system(getSystemPrompt())
                    .system(nextStepReminder())
                    .tools(availableTools)
                    .call()
                    .chatResponse();
            // 记录响应，用于的等下执行 Act
            this.toolCallChatResponse = chatResponse;

            // 3.解析工具调用结果，获取要调用的工具
            // 助手消息
            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
            // 获取要调用的工具列表
            List<AssistantMessage.ToolCall> toolCallList = assistantMessage.getToolCalls();
            // 输出提示信息
            String result = assistantMessage.getText();
            log.info(getName() + "的思考：" + result);
            this.lastThinkText = (result == null) ? "" : result;
            log.info(getName() + "选择调用了：" + toolCallList.size() + "个工具来使用");
            String toolCallInfo = toolCallList.stream()
                    .map(toolCall ->
                            String.format("工具名称：%s，工具参数：%s", toolCall.name(), toolCall.arguments()))
                    .collect(Collectors.joining("\n"));
            log.info(toolCallInfo);
            // 模型正常返回，重置连续失败计数
            consecutiveErrorCount = 0;
            // 如果不需要调用工具，返回 false
            if (toolCallList.isEmpty()) {
                // 只有不调用工具时，才需要手动记录助手消息
                getMessageList().add(assistantMessage);
                setState(AgentState.FINISHED);
                return false;
            } else {
                // 调用工具时，会自动记录
                return true;
            }
        } catch (Exception e) {
            log.error(getName() + "的思考过程遇到问题：", e);
            getMessageList().add(new AssistantMessage("处理时遇到了错误" + e.getMessage()));
            // ②连续失败阈值：判断任务无法推进，直接置 FINISHED 终止
            consecutiveErrorCount++;
            if (consecutiveErrorCount >= MAX_CONSECUTIVE_ERRORS) {
                log.error(getName() + "连续{}次思考失败，任务提前终止", consecutiveErrorCount);
                setState(AgentState.FINISHED);
            }
            return false;
        }
    }

    /** 每次任务开始前重置单次任务状态，防止 Agent 实例复用时把上轮计数带过来 */
    @Override
    protected void resetStepState() {
        this.stepCount = 0;
        this.nextStepPromptInjected = false;
    }

    /**
     * 每一步都要告诉模型的「当前该做什么」。
     * 这里只描述行动原则和进度，不放静态硬性规则——
     * 规则属于 systemPrompt（见 ShiroManus），不要在每步上下文里反复累积。
     * 另外：这条提示作为 system 指令临时附加到本次调用，不写入 messageList，
     * 所以不会随步数线性堆积、把上下文占满。
     */
    protected String nextStepReminder() {
        if (!isNextStepPromptInjected()) {
            return "";
        }
        return StrUtil.format(
                "当前是第 {}/{} 步。请基于已有信息判断：信息不足就调用工具补充；"
                        + "信息已经足够就直接给出完整最终答复，不要再调用工具，也不要为了凑步骤而继续检索。",
                stepCount, getMaxSteps());
    }

    /** 不再需要行动时，把模型的最终回答作为本步骤的结果返回 */
    @Override
    protected String getConclusion() {
        return StrUtil.isNotBlank(lastThinkText) ? lastThinkText : "思考完成，无需行动";
    }

    /**
     * 执行工具调用并处理结果
     *
     * @return 执行结果
     */
    @Override
    public String act() {
        if (!toolCallChatResponse.hasToolCalls()) {
            return "无工具需要调用";
        }
        //调用工具
        Prompt prompt = new Prompt(getMessageList(), this.chatOptions);
        ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, toolCallChatResponse);
        // 记录消息上下文，conversationHistory() 已经包含了助手消息和工具调用返回的结果
        setMessageList(toolExecutionResult.conversationHistory());
        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) CollUtil.getLast(toolExecutionResult.conversationHistory());
        // 判断是否调用了终止工具（tools.TerminateTool.java）
        boolean terminateToolCalled = toolResponseMessage.getResponses().stream()
                .anyMatch(response -> response.name().equals("doTerminate"));
        if (terminateToolCalled) {
            // 任务结束，更改状态
            setState(AgentState.FINISHED);
        }

        String results = toolResponseMessage.getResponses().stream()
                .map(response -> "工具" + response.name() + "返回的结果：" + response.responseData())
                .collect(Collectors.joining("\n"));
        log.info(results);
        if (StrUtil.isNotBlank(lastThinkText)) {
            return lastThinkText + "\n" + results;
        }
        return results;
    }
}

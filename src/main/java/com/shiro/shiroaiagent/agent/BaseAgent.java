package com.shiro.shiroaiagent.agent;

import cn.hutool.core.util.StrUtil;
import com.shiro.shiroaiagent.agent.model.AgentState;
import com.shiro.shiroaiagent.agent.model.AgentStateException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Data
public abstract class BaseAgent {

    // 核心属性
    private String name;

    // 提示词
    private String systemPrompt;
    private String nextStepPrompt;

    // 代理状态
    private AgentState state = AgentState.IDLE;

    // 执行步骤控制
    private int currentStep = 0;
    private int maxSteps = 10;        // 根据经费调节

    // LLM 大模型
    private ChatClient chatClient;    // 使用Spring AI 提供的 ChatClient

    // 上下文记忆
    private List<Message> messageList = new ArrayList<>();


    /**
     * 运行代理
     *
     * @param userPrompt 用户提示词
     * @return 执行结果
     */
    public String run(String userPrompt) {
        // 1.基础校验
        if (this.state != AgentState.IDLE) {
            throw new AgentStateException(
                    "Agent当前状态不可用" + this.state
            );
        }
        if (StrUtil.isBlank(userPrompt)) {
            throw new AgentStateException(
                    "Prompt不能为空"
            );
        }
        // 2.执行，更改状态
        this.state = AgentState.RUNNING;
        // 记录消息上下文
        messageList.add(new UserMessage(userPrompt));
        // 保存结果列表
        List<String> results = new ArrayList<>();
        try {
            // 执行循环
            for (int currentStep = 0; currentStep < maxSteps && state != AgentState.FINISHED; currentStep++) {
                int stepNumber = currentStep + 1;
                log.info("执行步骤 {}/{}", stepNumber,maxSteps);
                // 单步执行
                String stepResult = step();
                String result = "步骤" + stepNumber + "：" + stepResult;
                results.add(result);
            }

            // 检查是否超出步骤限制
            if (currentStep >= maxSteps) {
                state = AgentState.FINISHED;
                results.add("进程结束：达到最大步骤数（" + maxSteps + ")");
            }
            return String.join("\n", results);
        } catch (Exception e) {
            state = AgentState.ERROR;
            log.error("Agent执行错误：" + e);
            return "执行错误" + e.getMessage();
        } finally {
            // 3.清理资源
            this.cleanUp();
        }
    }

    /**
     * 运行代理（流式输出）
     *
     * @param userPrompt 用户提示词
     * @return 执行结果
     */
    public SseEmitter runStream(String userPrompt) {
        // 创建一个超时时间较长的 SseEmitter
        SseEmitter sseEmitter = new SseEmitter(300000L);    // 5 分钟超时
        // *使用线程异步处理，避免阻塞主线程*
        CompletableFuture.runAsync(() -> {
            // 1.基础校验
            try {
                if (this.state != AgentState.IDLE) {
                    sseEmitter.send("错误，无法从状态运行代理：" + this.state);
                    sseEmitter.complete();
                    return;
                }
                if (StrUtil.isBlank(userPrompt)) {
                    sseEmitter.send("错误，不能使用空提示词运行代理");
                    sseEmitter.complete();
                    return;
                }
            } catch (IOException e) {
                sseEmitter.completeWithError(e);
            }
            // 2.执行，更改状态
            this.state = AgentState.RUNNING;
            // 记录消息上下文
            messageList.add(new UserMessage(userPrompt));
            resetStepState();
            // 保存结果列表
            List<String> results = new ArrayList<>();
            try {
                // 执行循环
                for (currentStep = 0; currentStep < maxSteps && state != AgentState.FINISHED; currentStep++) {
                    int stepNumber = currentStep + 1;
                    log.info("执行步骤 {}/{}", stepNumber,maxSteps);
                    // 单步执行
                    String stepResult = step();
                    String result = "步骤" + stepNumber + "：" + stepResult;
                    results.add(result);
                    // 输出当前每一步的结果到 SSE
                    sseEmitter.send(result);
                }

                // 检查是否超出步骤限制
                if (currentStep >= maxSteps) {
                    state = AgentState.FINISHED;
                    results.add("进程结束：达到最大步骤数（" + maxSteps + ")");
                    sseEmitter.send("执行结束：达到最大步骤：（" + maxSteps + "）" );
                }
                // 正常完成
                sseEmitter.complete();
            } catch (Exception e) {
                state = AgentState.ERROR;
                log.error("Agent执行错误：" + e);
                try {
                    sseEmitter.send("执行错误：" + e.getMessage());
                    sseEmitter.complete();
                } catch (IOException ex) {
                    sseEmitter.completeWithError(ex);
                }
            } finally {
                // 3.清理资源
                this.cleanUp();
            }
        });

        // 设置超时回调
        sseEmitter.onTimeout(() -> {
            this.state = AgentState.ERROR;
            this.cleanUp();
            log.info("SSE 连接超时");
        });
        // 设置完成回调
        sseEmitter.onCompletion(() -> {
            if (this.state == AgentState.RUNNING) {
                this.state = AgentState.FINISHED;
            }
            this.cleanUp();
            log.info("SSE 连接完成");
        });

        return sseEmitter;
    }


    /**
     * 定义单个步骤
     *
     * @return
     */
    public abstract String step();


    /**
     * 每次任务开始前重置「单次任务」的状态。
     * 子类（如 ToolCallAgent 的步数计数与提示注入标记）重写本方法，
     * 避免 Agent 实例被复用时把上一个任务的计数带过来。
     */
    protected void resetStepState() {
    }

    /**
     * 清理资源
     */
    protected void cleanUp() {
        // 子类可以重写此方法来清理资源
    }
}
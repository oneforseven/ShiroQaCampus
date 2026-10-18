package com.shiro.shiroaiagent.agent;

import com.shiro.shiroaiagent.advisor.MyLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;


/**
 * AI 智能体（拥有自主规划能力）
 */
@Component
public class ShiroManus extends ToolCallAgent {

    public ShiroManus(ToolCallback[] allTools, ChatModel dashscopeChatModel) {
        super(allTools);
        this.setName("ShiroManus");
        String SYSTEM_PROMPT = """  
                你是ShiroManus，一个全能的AI助手，旨在解决用户提出的任何任务。你有各种工具可以使用，以高效完成复杂的请求。  
                """;
        this.setSystemPrompt(SYSTEM_PROMPT);
        String NEXT_STEP_PROMPT = """
                根据用户需求，主动选择最合适的工具或工具组合。
                对于复杂任务，可以把问题拆解、分步使用不同工具解决。
                每次使用工具后，用一两句话向用户说明结果，并给出下一步计划。
                【必须遵守】
                1. 始终使用中文回答。
                2. 搜索最多 3 次。信息已经足够时必须立即停止搜索，不要再找"更全的资料"。
                3. 任务完成后，必须先输出一段面向用户的完整结论（含具体地点、时间、路线等可直接使用的信息），
                   然后再调用 terminate 结束。
                4. 仅当用户明确要求 PDF/文档/文件格式时才生成文件；否则一律直接在对话中输出内容，不要自作主张生成文件。
                5. 禁止用"让我查看…："这类过渡句结束回复；每条回复要么调用工具，要么是完整结论。
                """;
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        this.setMaxSteps(25);
        // 初始化客户端
        ChatClient chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
        this.setChatClient(chatClient);
    }
}


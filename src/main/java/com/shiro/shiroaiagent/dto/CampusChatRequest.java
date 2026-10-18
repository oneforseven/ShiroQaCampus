package com.shiro.shiroaiagent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 校园问答请求
 */
@Data
@Schema(description = "校园问答请求（双通道：知识库检索 + 个人数据查询）")
public class CampusChatRequest {

    @Schema(description = "用户问题", example = "我的绩点是多少？")
    private String message;

    @Schema(description = "会话 id，用于多轮记忆；同一个 id 连续提问会带上历史上下文",
            example = "chat-001")
    private String chatId;
}

package com.shiro.shiroaiagent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 校园问答响应
 *
 * 除了答案本身，还返回"走了哪条通道""命中了几条知识""有没有查数据库"，
 */
@Data
@Schema(description = "校园问答响应")
public class CampusChatResponseVO {

    @Schema(description = "回答内容")
    private String answer;

    @Schema(description = "命中的通道：RAG（仅知识库）/ Tool（仅数据查询）/ RAG+Tool（两者都用了）/ 无依据",
            example = "RAG+Tool")
    private String channel;

    @Schema(description = "知识库是否命中")
    private boolean hitKnowledgeBase;

    @Schema(description = "是否查询了业务数据库（个人数据或办事地点）")
    private boolean usedDatabase;

    @Schema(description = "命中的知识库片段数")
    private int kbHitCount;

    @Schema(description = "回答依据列表")
    private List<CitationVO> sources;

    @Schema(description = "端到端耗时（毫秒）")
    private long latencyMs;

    @Schema(description = "是否命中问答缓存（命中则未经过检索与大模型，速度极快）")
    private boolean cacheHit;
}

package com.shiro.shiroaiagent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 回答依据（引用来源）
 *
 * 双通道架构下有两类来源，前端需要区分展示：
 *
 * - {@code type = 知识库}：来自向量检索的校园文档片段
 * - {@code type = 数据库}：来自教务数据库的实时查询结果（成绩、绩点、办事地点）
 *
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "回答依据")
public class CitationVO {

    @Schema(description = "来源类型：知识库 / 数据库", example = "知识库")
    private String type;

    @Schema(description = "来源标题（文档名或数据表名）", example = "本科生学籍管理规定")
    private String title;

    @Schema(description = "片段摘要或说明", example = "学生每学期修读学分不得低于……")
    private String snippet;
}

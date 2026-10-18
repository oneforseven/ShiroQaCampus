package com.shiro.shiroaiagent.tools;

import com.shiro.shiroaiagent.entity.CampusPlace;
import com.shiro.shiroaiagent.service.CampusPlaceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;

/**
 * 校园办事地点查询工具（校园导航类问题）
 *
 * "去哪办、几点办公、电话多少"这类问题有确定答案，属于结构化数据，
 * 交给工具查询而不是让模型生成，避免编造地点与办公时间。
 *
 * 同样采用"每请求一个实例"的写法，便于记录本次是否调用过工具（用于来源标注）。
 */
@Slf4j
public class CampusPlaceTool {

    private final CampusPlaceService campusPlaceService;

    /** 本次请求是否调用过本工具 */
    private boolean invoked = false;

    public CampusPlaceTool(CampusPlaceService campusPlaceService) {
        this.campusPlaceService = campusPlaceService;
    }

    @Tool(description = """
            查询校内办事地点信息：办什么事去哪里、在哪个楼哪个房间、办公时间、联系电话。
            参数 keyword 是办事关键词，例如"学生证""成绩单""缓考""三方协议""校园卡""上网账号""奖学金""档案"。

            以下情形都应调用本工具（即使问法模糊、没有出现"去哪"这样的字眼）：
            - "XX 去哪办""XX 办公室在哪""XX 几点上班""XX 电话多少"；
            - "我的学生证丢了怎么办""XX 补办流程是什么""补办 XX 需要什么材料"——
              这类问题虽然问的是"怎么办/流程/材料"，但首先要给出【办理地点、办公时间与联系电话】。

            调用后如需补充办理流程与所需材料，可结合知识库检索结果一并说明。""")
    public String queryCampusPlace(
            @ToolParam(description = "办事关键词或事项名称，例如：学生证、缓考、三方协议、图书馆") String keyword) {

        invoked = true;
        List<CampusPlace> places = campusPlaceService.search(keyword);
        if (places.isEmpty()) {
            return "没有找到与「" + keyword + "」相关的办事地点，可以建议用户联系辅导员或学校总服务台。";
        }

        StringBuilder sb = new StringBuilder("办事地点查询结果：\n");
        for (CampusPlace p : places) {
            sb.append("- ").append(p.getName()).append("\n")
                    .append("  地点：").append(nullToDash(p.getBuilding())).append(" ")
                    .append(nullToDash(p.getRoom())).append("（").append(nullToDash(p.getAddress())).append("）\n")
                    .append("  办公时间：").append(nullToDash(p.getOfficeHours())).append("\n")
                    .append("  联系电话：").append(nullToDash(p.getPhone())).append("\n");
        }
        return sb.toString();
    }

    /** 本次请求是否调用过本工具 */
    public boolean isInvoked() {
        return invoked;
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}

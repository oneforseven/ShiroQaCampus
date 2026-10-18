package com.shiro.shiroaiagent.controller;

import com.shiro.shiroaiagent.entity.CampusPlace;
import com.shiro.shiroaiagent.service.CampusPlaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 校园导航接口（办事地点、办公时间、联系方式）
 */
@Tag(name = "校园导航", description = "办事地点、办公时间与联系方式（结构化查询，不走大模型生成）")
@RestController
@RequestMapping("/campus")
public class CampusPlaceController {

    @Resource
    private CampusPlaceService campusPlaceService;

    @Operation(summary = "按关键词查询办事地点",
            description = "关键词会匹配地点名称、检索关键词、分类与楼栋，例如：学生证、缓考、三方协议")
    @GetMapping("/places")
    public List<CampusPlace> places(
            @Parameter(description = "关键词；不传则返回全部地点")
            @RequestParam(required = false) String keyword) {
        return campusPlaceService.search(keyword);
    }
}

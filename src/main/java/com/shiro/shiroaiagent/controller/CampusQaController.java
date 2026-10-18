package com.shiro.shiroaiagent.controller;

import cn.hutool.core.util.StrUtil;
import com.shiro.shiroaiagent.app.CampusQaApp;
import com.shiro.shiroaiagent.dto.CampusChatRequest;
import com.shiro.shiroaiagent.security.CurrentUserHolder;
import com.shiro.shiroaiagent.security.LoginUser;
import com.shiro.shiroaiagent.vo.CampusChatResponseVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 校园问答接口（双通道，需要登录）
 *
 * 关键点：身份在请求线程解析好再交给 AI 应用。
 * 若在 AI 的异步/流式线程里读 Sa-Token 上下文会拿不到身份，
 * 因此这里先取 {@link LoginUser}，再作为参数传进去。
 */
@Tag(name = "校园问答（双通道）", description = "知识库检索 + 个人数据查询；需要登录")
@RestController
@RequestMapping("/ai/campus")
public class CampusQaController {

    @Resource
    private CampusQaApp campusQaApp;

    @Operation(summary = "校园问答",
            description = "非结构化知识走知识库检索，个人数据（成绩/绩点/学分）与办事地点走工具实时查询。" +
                    "返回结果包含 channel（走了哪条通道）与 sources（引用来源），前端可据此展示来源标注。")
    @PostMapping("/chat")
    public CampusChatResponseVO chat(@RequestBody CampusChatRequest request) {
        // ★ 必须在请求线程取身份，再传入异步链路
        LoginUser loginUser = CurrentUserHolder.currentLoginUser();
        String chatId = StrUtil.blankToDefault(request.getChatId(), "default");
        return campusQaApp.answer(request.getMessage(), chatId, loginUser);
    }
}

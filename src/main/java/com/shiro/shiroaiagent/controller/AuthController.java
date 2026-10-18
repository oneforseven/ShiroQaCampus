package com.shiro.shiroaiagent.controller;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.shiro.shiroaiagent.dto.LoginRequest;
import com.shiro.shiroaiagent.security.CurrentUserHolder;
import com.shiro.shiroaiagent.security.LoginUser;
import com.shiro.shiroaiagent.service.AuthService;
import com.shiro.shiroaiagent.vo.LoginResponseVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 登录鉴权接口
 */
@Tag(name = "登录鉴权", description = "登录、登出、当前登录用户")
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Resource
    private AuthService authService;

    @Operation(summary = "登录",
            description = "学生用学号登录（如 2022001），管理员用 admin；模拟数据密码统一为 123456。" +
                    "登录成功后，后续请求需携带请求头 satoken: <tokenValue>")
    @PostMapping("/login")
    public LoginResponseVO login(@RequestBody LoginRequest request) {
        LoginUser user = authService.login(request.getUsername(), request.getPassword());
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
        return new LoginResponseVO(tokenInfo.getTokenName(), tokenInfo.getTokenValue(), user);
    }

    @Operation(summary = "登出")
    @PostMapping("/logout")
    public Map<String, Object> logout() {
        authService.logout();
        return Map.of("success", true);
    }

    @Operation(summary = "当前登录用户", description = "前端用它判断是否已登录、以及展示当前身份")
    @GetMapping("/me")
    public LoginUser me() {
        return CurrentUserHolder.currentLoginUser();
    }
}

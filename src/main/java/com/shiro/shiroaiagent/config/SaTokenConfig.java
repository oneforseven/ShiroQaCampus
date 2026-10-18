package com.shiro.shiroaiagent.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 拦截器配置：受保护路径必须登录
 *
 * <p><b>受保护：</b>{@code /student/**}（学生个人数据）、{@code /admin/**}（管理端）、
 * {@code /ai/campus/**}（双通道问答，需要身份才能查个人数据）。</p>
 *
 * <p><b>暂不强制登录：</b>旧的 {@code /ai/shiro_app/**}、{@code /ai/manus/**} 接口
 * （现有前端在用，等前端加上登录页后一并收紧）、{@code /campus/**}（公共办事信息）、
 * 以及 Knife4j 文档相关路径。</p>
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
                .addPathPatterns(
                        "/student/**",
                        "/admin/**",
                        "/ai/campus/**"
                )
                .excludePathPatterns(
                        "/auth/**",
                        "/health",
                        "/files/**",
                        // 现有前端在用的旧问答接口：下一步随前端登录页一起收紧
                        "/ai/shiro_app/**",
                        "/ai/manus/**",
                        // Knife4j / OpenAPI 文档（方便本地自测）
                        "/doc.html",
                        "/webjars/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/favicon.ico"
                );
    }
}

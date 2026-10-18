package com.shiro.shiroaiagent.tools;


import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 获取当前时间的工具。
 * 标 @Component：由 Spring 创建并管理这一个实例，
 * 应用里注入它、集成测试里注入它，都和 AI 调用的是同一个 bean。
 */
@Component
public class GetCurrentTimeTool implements AITools{

    private static final DateTimeFormatter DEFAULT_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Tool(description = "获取当前时间，返回格式 yyyy-MM-dd HH:mm:ss（系统默认时区）")
    public String getCurrentTime() {
        return ZonedDateTime.now().format(DEFAULT_FORMATTER);
    }

    @Tool(description = "按指定格式和时区获取当前时间，格式遵循 java.time.format.DateTimeFormatter")
    public String getCurrentTimeAs(
            @ToolParam(description = "格式串，例如 yyyy-MM-dd HH:mm:ss 或 HH:mm", required = false) String pattern,
            @ToolParam(description = "IANA 时区 ID，例如 Asia/Shanghai、UTC，缺省用系统默认时区", required = false) String zoneId) {
        String fmt = (pattern == null || pattern.isBlank()) ? "yyyy-MM-dd HH:mm:ss" : pattern;
        ZoneId zone = (zoneId == null || zoneId.isBlank()) ? ZoneId.systemDefault() : ZoneId.of(zoneId);
        return ZonedDateTime.now(zone).format(DateTimeFormatter.ofPattern(fmt));
    }
}
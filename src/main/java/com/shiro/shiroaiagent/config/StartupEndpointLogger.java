package com.shiro.shiroaiagent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 启动自检：把已注册的接口打印到日志
 * 用途：排查"某个 Controller 是否真的注册成功"。
 * 应用启动后会在控制台输出所有已注册的接口路径。
 */
@Component
@Slf4j
public class StartupEndpointLogger {

    private final RequestMappingHandlerMapping handlerMapping;

    public StartupEndpointLogger(RequestMappingHandlerMapping handlerMapping) {
        this.handlerMapping = handlerMapping;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logEndpoints() {
        log.info("==================== 已注册接口清单 ====================");
        handlerMapping.getHandlerMethods().forEach((info, method) -> {
            String patterns = info.getPathPatternsCondition() != null
                    ? info.getPathPatternsCondition().getPatternValues().toString()
                    : String.valueOf(info.getPatternValues());
            log.info("{}  ->  {}.{}", patterns, method.getBeanType().getSimpleName(), method.getMethod().getName());
        });
        log.info("=======================================================");
    }
}

package com.shiro.shiroaiagent.tools;

import jakarta.annotation.Resource;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 集中的工具注册类
 */
@Configuration
public class ToolRegistration {

//    @Resource
//    private WebSearchTool webSearchTool;
//
//    @Resource
//    private GetCurrentTimeTool getCurrentTimeTool;
//
//    @Resource
//    private WebScrapingTool webScrapingTool;

//    @Bean
//    public ToolCallback[] allTools() {
//        FileOperationTool fileOperationTool = new FileOperationTool();
//        ResourceDownloadTool resourceDownloadTool = new ResourceDownloadTool();
//        TerminalOperationTool terminalOperationTool = new TerminalOperationTool();
//        PDFGenerationTool pdfGenerationTool = new PDFGenerationTool();
//        // 转换成能给Spring AI用的工具
//        return ToolCallbacks.from(
//                fileOperationTool,
//                webSearchTool,
//                webScrapingTool,
//                resourceDownloadTool,
//                terminalOperationTool,
//                pdfGenerationTool,
//                getCurrentTimeTool
//        );
//    }

    // 工具多的话用标记接口让Spring 自动收集
    @Bean
    public ToolCallback[] allTools(List<AITools> allAITools) {
        return ToolCallbacks.from(allAITools.toArray());
    }
}

package com.shiro.shiroaiagent.tools;


import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;


/**
 * 网页抓取工具
 */
//@Component
//public class WebScrapingTool implements AITools{
//
//    @Tool(description = "Scrape the content of a web page")
//    public String scrapeWebPage(@ToolParam(description = "URL of the web page to scrape") String url) {
//        try {
//            Document document = Jsoup.connect(url).get();
//            return document.html();
//        } catch (Exception e) {
//            return "抓取网页失败：" + e.getMessage();
//        }
//    }
//}
@Component
public class WebScrapingTool implements AITools {

    private static final int TIMEOUT_MS = 15_000;
    private static final int MAX_TEXT_LEN = 6_000;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/124.0 Safari/537.36";

    @Tool(description = "Scrape the text content of a web page")
    public String scrapeWebPage(@ToolParam(description = "URL of the web page to scrape") String url) {
        try {
            Document document = Jsoup.connect(url)
                    .timeout(TIMEOUT_MS)          // 超时
                    .userAgent(USER_AGENT)        // 减少被 403
                    .get();

            String title = document.title();
            // .text() 会去掉 script/style 标签内容，只留可见文本
            String bodyText = document.body() != null ? document.body().text() : "";
            bodyText = bodyText.replaceAll("\\s+", " ").trim(); // 压缩空白

            String content = bodyText.length() > MAX_TEXT_LEN
                    ? bodyText.substring(0, MAX_TEXT_LEN) + "\n...[内容过长已截断]"
                    : bodyText;

            return "标题：" + title + "\n正文：\n" + content;
        } catch (Exception e) {
            return "抓取网页失败：" + e.getMessage();
        }
    }
}

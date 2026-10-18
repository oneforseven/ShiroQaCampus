package com.shiro.shiroaiagent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

//@Component
//public class WebSearchTool implements AITools{
//
//    private static final String SEARCH_API_URL = "https://www.searchapi.io/api/v1/search";
//
//    private final OkHttpClient httpClient = new OkHttpClient();
//    private final ObjectMapper objectMapper = new ObjectMapper();
//
//    @Value("${search-api.api-key}")
//    private String apiKey;
//
//    @Tool(description = "Search web pages from Baidu using SearchApi")
//    public String searchWeb(
//            @ToolParam(description = "Search query keywords") String query
//    ) {
//        if (apiKey == null || apiKey.isBlank()) {
//            return "搜索失败：未配置 search-api.api-key";
//        }
//
//        HttpUrl url = HttpUrl.get(SEARCH_API_URL)
//                .newBuilder()
//                .addQueryParameter("engine", "baidu")
//                .addQueryParameter("q", query)
//                .addQueryParameter("api_key", apiKey)
//                .build();
//
//        Request request = new Request.Builder()
//                .url(url)
//                .get()
//                .build();
//
//        try (Response response = httpClient.newCall(request).execute()) {
//            if (!response.isSuccessful() || response.body() == null) {
//                return "搜索失败：HTTP " + response.code();
//            }
//
//            JsonNode root = objectMapper.readTree(response.body().string());
//            JsonNode organicResults = root.path("organic_results");
//
//            if (!organicResults.isArray() || organicResults.isEmpty()) {
//                return "没有搜索到相关结果";
//            }
//
//            StringBuilder result = new StringBuilder("搜索结果：\n\n");
//
//            for (int i = 0; i < Math.min(5, organicResults.size()); i++) {
//                JsonNode item = organicResults.get(i);
//                result.append(i + 1).append(". ")
//                        .append(item.path("title").asText(""))
//                        .append("\n摘要：")
//                        .append(item.path("snippet").asText(""))
//                        .append("\n链接：")
//                        .append(item.path("link").asText(""))
//                        .append("\n\n");
//            }
//
//            return result.toString();
//        } catch (IOException e) {
//            return "搜索失败：" + e.getMessage();
//        }
//    }
//}
@Component
public class WebSearchTool implements AITools {

    private static final String SEARCH_API_URL = "https://www.searchapi.io/api/v1/search";
    private static final int MAX_RESULTS = 5;
    private static final int MAX_SNIPPET_LEN = 200;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${search-api.api-key}")
    private String apiKey;

    @Tool(description = "Search web pages from Baidu using SearchApi")
    public String searchWeb(@ToolParam(description = "Search query keywords") String query) {
        if (apiKey == null || apiKey.isBlank()) {
            return "搜索失败：未配置 search-api.api-key";
        }

        HttpUrl url = HttpUrl.get(SEARCH_API_URL)
                .newBuilder()
                .addQueryParameter("engine", "baidu")
                .addQueryParameter("q", query)
                .addQueryParameter("api_key", apiKey)
                .build();

        Request request = new Request.Builder().url(url).get().build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return "搜索失败：HTTP " + response.code();
            }

            JsonNode root = objectMapper.readTree(response.body().string());
            JsonNode organicResults = root.path("organic_results");

            if (!organicResults.isArray() || organicResults.isEmpty()) {
                return "没有搜索到相关结果";
            }

            StringBuilder result = new StringBuilder("搜索结果：\n\n");
            for (int i = 0; i < Math.min(MAX_RESULTS, organicResults.size()); i++) {
                JsonNode item = organicResults.get(i);
                String title = item.path("title").asText("");
                String snippet = item.path("snippet").asText("");
                if (snippet.length() > MAX_SNIPPET_LEN) {
                    snippet = snippet.substring(0, MAX_SNIPPET_LEN) + "...";
                }
                result.append(i + 1).append(". ")
                        .append(title)
                        .append("\n摘要：").append(snippet)
                        .append("\n链接：").append(item.path("link").asText(""))
                        .append("\n\n");
            }
            return result.toString();
        } catch (IOException e) {
            return "搜索失败：" + e.getMessage();
        }
    }
}
package com.shiro.shiroaiagent.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.http.HttpUtil;
import com.shiro.shiroaiagent.constant.FileConstant;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * 资源下载工具
 */
//@Component
//public class ResourceDownloadTool implements AITools{
//
//    @Tool(description = "Download a resource from a given URL")
//    public String downloadResource(@ToolParam(description = "URL of the resource to download") String url, @ToolParam(description = "Name of the file to save the downloaded resource") String fileName) {
//        String fileDir = FileConstant.FILE_SAVE_DIR + "/download";
//        String filePath = fileDir + "/" + fileName;
//        try {
//            // 创建目录
//            FileUtil.mkdir(fileDir);
//            // 使用 Hutool 的 downloadFile 方法下载资源
//            HttpUtil.downloadFile(url, new File(filePath));
//            return "Resource downloaded successfully to: " + filePath;
//        } catch (Exception e) {
//            return "Error downloading resource: " + e.getMessage();
//        }
//    }
//}
@Component
public class ResourceDownloadTool implements AITools {

    private static final int TIMEOUT_MS = 30_000;
    private static final int MAX_FILE_NAME_LEN = 100;

    @Tool(description = "Download a resource from a given URL")
    public String downloadResource(@ToolParam(description = "URL of the resource to download") String url,
                                   @ToolParam(description = "Name of the file to save the downloaded resource") String fileName) {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            return "Error downloading resource: 不支持的 URL 协议";
        }
        String safeName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (safeName.length() > MAX_FILE_NAME_LEN) {
            safeName = safeName.substring(0, MAX_FILE_NAME_LEN);
        }

        String fileDir = FileConstant.FILE_SAVE_DIR + "/download";
        String filePath = fileDir + "/" + safeName;
        try {
            FileUtil.mkdir(fileDir);
            // 带超时下载（Hutool 重载方法，最后一个参数为毫秒超时）
            HttpUtil.downloadFile(url, new File(filePath), TIMEOUT_MS);
            return "Resource downloaded successfully to: " + filePath;
        } catch (Exception e) {
            return "Error downloading resource: " + e.getMessage();
        }
    }
}
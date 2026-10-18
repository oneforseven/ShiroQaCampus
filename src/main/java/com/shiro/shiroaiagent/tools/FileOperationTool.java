package com.shiro.shiroaiagent.tools;


import cn.hutool.core.io.FileUtil;
import com.shiro.shiroaiagent.constant.FileConstant;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * 文件操作工具类（提供文件读写功能）
 */
@Component
public class FileOperationTool implements AITools{

    // 读写文件根目录地址
    private final String FILE_DIR = FileConstant.FILE_SAVE_DIR + "/file";

    @Tool(description = "Read the content of a file by relative file path")
    public String readFile(@ToolParam(description = "Relative file path under the allowed file workspace, such as notes/todo.txt") String fileName) {
        String filePath = FILE_DIR + "/" + fileName;
        try {
            return FileUtil.readUtf8String(filePath);
        } catch (Exception e) {
            return "读取文件失败，失败文件名：" + e.getMessage();
        }
    }

    @Tool(description = "Write content to a file by relative file path. If the file exists, it will be overwritten")
    public String writeFile(@ToolParam(description = "Relative file path under the allowed file workspace, such as notes/todo.txt") String fileName,
                            @ToolParam(description = "Text content to write into the file") String content) {
        String filePath = FILE_DIR + "/" + fileName;

        try {
            // 创建目录
            FileUtil.mkdir(FILE_DIR);
            FileUtil.writeUtf8String(content, filePath);
            return "文件已被成功写入到：" + filePath;
        } catch (Exception e) {
            return "错误写入文件：" + e.getMessage();
        }
    }
}

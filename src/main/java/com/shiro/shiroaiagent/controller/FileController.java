package com.shiro.shiroaiagent.controller;

import com.shiro.shiroaiagent.constant.FileConstant;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 生成文件的下载接口
 */
@RestController
@RequestMapping("/files")
public class FileController {

    /** 下载 tmp/pdf 目录下的 PDF */
    @GetMapping("/pdf/{fileName}")
    public ResponseEntity<Resource> downloadPdf(@PathVariable String fileName) {
        // 防路径穿越：文件名里不允许出现 .. / \
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            return ResponseEntity.badRequest().build();
        }
        File file = new File(FileConstant.FILE_SAVE_DIR + "/pdf/" + fileName);
        if (!file.exists() || !file.isFile()) {
            return ResponseEntity.notFound().build();
        }
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded)
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(file.length())
                .body(new FileSystemResource(file));
    }
}
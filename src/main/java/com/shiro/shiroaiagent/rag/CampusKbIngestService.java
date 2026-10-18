package com.shiro.shiroaiagent.rag;

import com.shiro.shiroaiagent.entity.KbDocument;
import com.shiro.shiroaiagent.service.KbDocumentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置校园语料初始化（把 {@code classpath:campus/*.md} 登记进 {@code kb_document} 并入库）
 */
@Service
@Slf4j
public class CampusKbIngestService {

    /** 内置语料：文件名 → 场景（仅用于引导，新增文档走上传接口） */
    private static final Map<String, String> SEED_SCENE_BY_FILE = Map.of(
            "学籍管理规定.md", "教务问答",
            "本科专业培养方案说明.md", "教务问答",
            "学生证与各类证明办理指南.md", "教务问答",
            "本科生奖学金评定办法.md", "学生事务",
            "校园生活服务指南.md", "校园生活",
            "图书馆借阅规则.md", "图书馆服务",
            "毕业生就业手续与三方协议办理指南.md", "就业服务"
    );

    private final ResourcePatternResolver resourcePatternResolver;

    private final KbDocumentService kbDocumentService;

    private final KbIngestService kbIngestService;

    public CampusKbIngestService(ResourcePatternResolver resourcePatternResolver,
                                 KbDocumentService kbDocumentService,
                                 KbIngestService kbIngestService) {
        this.resourcePatternResolver = resourcePatternResolver;
        this.kbDocumentService = kbDocumentService;
        this.kbIngestService = kbIngestService;
    }

    /**
     * 初始化内置语料（幂等：重复调用不会重复入库）
     *
     * @return 入库统计：扫描数 / 新入库数 / 跳过数 / 各文件片段数 / 失败列表
     */
    public Map<String, Object> seedBuiltIn() {
        Map<String, Object> result = new LinkedHashMap<>();

        // ---------- 阶段一：清理旧流程遗留的"无主向量" ----------
        try {
            int purged = kbIngestService.purgeLegacyVectors();
            result.put("purgedLegacyVectors", purged);
        } catch (Exception e) {
            result.put("stage", "阶段一【清理历史无主向量】失败");
            result.put("error", e.toString());
            log.error("清理历史无主向量失败", e);
            return result;
        }

        // ---------- 阶段二：逐篇登记 + 入库（单篇失败不影响其他） ----------
        Map<String, Integer> chunksPerFile = new LinkedHashMap<>();
        List<String> failedFiles = new ArrayList<>();
        int scanned = 0;
        int ingested = 0;
        int skipped = 0;
        int totalChunks = 0;

        try {
            Resource[] resources = resourcePatternResolver.getResources("classpath:campus/*.md");
            scanned = resources.length;
            log.info("扫描到内置语料文件 {} 个", scanned);

            for (Resource resource : resources) {
                String fileName = resource.getFilename();
                try {
                    if (kbDocumentService.existsByFileName(fileName)) {
                        skipped++;
                        log.info("内置语料已存在，跳过：{}", fileName);
                        continue;
                    }
                    String scene = SEED_SCENE_BY_FILE.getOrDefault(fileName, "其他");
                    byte[] content = resource.getContentAsByteArray();
                    KbDocument doc = kbDocumentService.registerAndIngest(fileName, scene, content);
                    ingested++;
                    int chunks = doc.getChunkCount() == null ? 0 : doc.getChunkCount();
                    chunksPerFile.put(fileName, chunks);
                    totalChunks += chunks;
                } catch (Exception e) {
                    failedFiles.add(fileName + " -> " + e.getMessage());
                    log.error("内置语料入库失败：{}", fileName, e);
                }
            }
        } catch (Exception e) {
            result.put("stage", "阶段二【扫描内置语料】失败");
            result.put("error", e.toString());
            log.error("扫描内置语料失败", e);
            return result;
        }

        result.put("stage", failedFiles.isEmpty() ? "内置语料初始化完成" : "内置语料初始化部分失败");
        result.put("scanned", scanned);
        result.put("ingested", ingested);
        result.put("skipped", skipped);
        result.put("chunksPerFile", chunksPerFile);
        result.put("totalChunks", totalChunks);
        result.put("failedFiles", failedFiles);
        log.info("内置语料初始化完成：扫描{}，新入库{}，跳过{}，片段合计{}，失败{}",
                scanned, ingested, skipped, totalChunks, failedFiles.size());
        return result;
    }
}

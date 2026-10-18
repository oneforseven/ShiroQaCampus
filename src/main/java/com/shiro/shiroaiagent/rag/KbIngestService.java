package com.shiro.shiroaiagent.rag;

import com.shiro.shiroaiagent.entity.KbDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 单篇文档的向量入库服务（切分 → 向量化 → 写 pgvector）
 *
 * 这是"文档级"的入库，不再有"整批"的概念：
 * 每篇文档的每个片段都带上 {@code docId} 元数据指回 {@code kb_document.id}，
 * 因此可以做到：</p>
 * <ul>
 *   <li><b>精确删除</b>：删一篇文档只删它自己的片段（{@code docId == 'N'}），
 *       不会波及其他文档；</li>
 *   <li><b>精确重建</b>：改了切分策略或文档内容，只重灌这一篇；</li>
 *   <li><b>失败可回滚</b>：中途写失败时把已写入的片段删掉，不留"残缺但能被检索到"的数据。</li>
 * </ul>
 *
 * <p><b>切分策略：</b>沿用项目既有的 {@link MyTokenTextSplitter}（TokenTextSplitter），
 */
@Service
@Slf4j
public class KbIngestService {

    /** 校园语料的元数据标记：检索时据此隔离历史示例文档 */
    public static final String DOC_TYPE = "campus";

    /** 文档归属的元数据键：值 = kb_document.id，删除/重建都靠它定位 */
    public static final String META_DOC_ID = "docId";

    /**
     * Embedding 批次大小
     * <p>DashScope 的 Embedding 接口单次最多接受 25 条文本，超过会抛
     * {@code The input texts limit 25.}，因此必须分批。</p>
     */
    private static final int EMBEDDING_BATCH_SIZE = 20;

    private final MyTokenTextSplitter tokenTextSplitter;

    private final VectorStore pgVectorStore;

    /** 仅用于清理"没有 docId 的历史遗留向量"（VectorStore 接口表达不了"缺字段"） */
    private final JdbcTemplate jdbcTemplate;

    public KbIngestService(MyTokenTextSplitter tokenTextSplitter,
                           VectorStore pgVectorStore,
                           JdbcTemplate jdbcTemplate) {
        this.tokenTextSplitter = tokenTextSplitter;
        this.pgVectorStore = pgVectorStore;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 把一篇 Markdown 文档切分并写入向量库
     *
     * @param doc    文档记录（取标题、场景、可见范围、id 作为元数据）
     * @param mdFile 磁盘上的 md 文件
     * @return 写入的片段数
     * @throws IllegalArgumentException 文件不存在
     * @throws IllegalStateException    向量化中途失败（已回滚已写入片段）
     */
    public int ingest(KbDocument doc, Path mdFile) {
        if (mdFile == null || !Files.isRegularFile(mdFile)) {
            throw new IllegalArgumentException("文档文件不存在，无法入库：" + (mdFile == null ? "(未设置)" : mdFile));
        }

        List<Document> chunks = split(doc, mdFile);
        if (chunks.isEmpty()) {
            log.warn("文档切分后没有任何片段（可能为空文件）：docId={}, file={}", doc.getId(), mdFile);
            return 0;
        }

        int written = 0;
        try {
            for (int i = 0; i < chunks.size(); i += EMBEDDING_BATCH_SIZE) {
                List<Document> batch = chunks.subList(i, Math.min(i + EMBEDDING_BATCH_SIZE, chunks.size()));
                pgVectorStore.add(batch);
                written += batch.size();
                log.info("文档 {} 向量化写入进度：{}/{}", doc.getId(), written, chunks.size());
            }
        } catch (Exception e) {
            // ⚠️ 半途失败若不回滚，这篇文档会留下"一部分片段"，检索时会返回残缺内容，
            //    而 kb_document 里又显示"未入库"——状态与数据不一致，最难排查。
            log.error("文档 {} 向量化写入失败，回滚已写入的 {} 条片段", doc.getId(), written, e);
            deleteByDocIdQuietly(doc.getId());
            Throwable root = e;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            throw new IllegalStateException("向量化写入失败：" + root.getMessage(), e);
        }

        log.info("文档入库完成：docId={}, title={}, 片段数={}", doc.getId(), doc.getTitle(), written);
        return written;
    }

    /**
     * 按 markdown 结构切分（先按分隔线切成多篇，再做 token 切分，检索粒度更细）
     */
    private List<Document> split(KbDocument doc, Path mdFile) {
        MarkdownDocumentReaderConfig.Builder builder = MarkdownDocumentReaderConfig.builder()
                .withHorizontalRuleCreateDocument(true)
                .withIncludeCodeBlock(false)
                .withIncludeBlockquote(false)
                .withAdditionalMetadata("filename", doc.getFileName())
                .withAdditionalMetadata("docTitle", doc.getTitle())
                .withAdditionalMetadata("scene", doc.getScene())
                .withAdditionalMetadata("docType", DOC_TYPE)
                // ★ 关键：片段归属到具体文档，删除/重建靠它定位
                .withAdditionalMetadata(META_DOC_ID, String.valueOf(doc.getId()))
                .withAdditionalMetadata("visibility", doc.getVisibility() == null ? "公开" : doc.getVisibility());
        if (doc.getDeptId() != null) {
            // 部门权限下推的预留元数据（本轮只写入，尚未参与检索过滤）
            builder.withAdditionalMetadata("deptId", String.valueOf(doc.getDeptId()));
        }
        List<Document> documents = new MarkdownDocumentReader(new FileSystemResource(mdFile), builder.build()).get();
        return tokenTextSplitter.splitDocuments(documents);
    }

    /**
     * 删除某篇文档的全部向量（严格版：失败会抛出，调用方必须中止删除流程）
     *
     * <p>为什么严格：如果向量没删掉却把 {@code kb_document} 记录删了，
     * 知识库里就会残留"已删除文档"的片段，问答时仍会引用到它——
     * 这正是"删了文档但还能搜到"的经典 bug。</p>
     */
    public void deleteByDocId(Long docId) {
        if (docId == null) {
            return;
        }
        try {
            pgVectorStore.delete(META_DOC_ID + " == '" + docId + "'");
            log.info("已删除文档 {} 的全部向量", docId);
        } catch (Exception e) {
            throw new IllegalStateException("删除文档 " + docId + " 的向量失败：" + e.getMessage(), e);
        }
    }

    /**
     * 删除某篇文档的向量（宽松版：失败只记日志）
     *
     * <p>只用于"入库前先清旧片段""入库失败回滚"这类<b>幂等清理</b>场景——
     * 此时删不掉也不影响流程推进，下次重灌会再删一次。</p>
     */
    public void deleteByDocIdQuietly(Long docId) {
        if (docId == null) {
            return;
        }
        try {
            pgVectorStore.delete(META_DOC_ID + " == '" + docId + "'");
        } catch (Exception e) {
            log.warn("清理文档 {} 的旧向量失败（可忽略，重灌时会再次清理）：{}", docId, e.getMessage());
        }
    }

    /**
     * 清理旧流程遗留的"无主向量"（metadata 里没有 docId 的校园语料片段）
     *
     * <p><b>为什么需要它：</b>旧的入库实现直接扫 jar 里的 md，写进向量库的片段
     * <b>没有 docId</b>，无法归属到任何 {@code kb_document} 记录。在"按文档删除"的新模型下，
     * 这些片段永远删不掉（不是任何文档的、也不受任何删除操作影响），
     * 却仍会被检索命中——相当于幽灵数据。因此内置语料初始化时显式清理一次。</p>
     *
     * <p>用 SQL 而不是 VectorStore 接口，是因为接口的过滤表达式无法表达"字段不存在"。</p>
     *
     * @return 清理掉的片段数
     */
    public int purgeLegacyVectors() {
        Integer removed = jdbcTemplate.update(
                "DELETE FROM vector_store WHERE metadata->>'docType' = ? AND (metadata->>'docId') IS NULL",
                DOC_TYPE);
        int count = removed == null ? 0 : removed;
        if (count > 0) {
            log.info("已清理 {} 条历史遗留的校园语料向量（无 docId，无法归属到文档记录）", count);
        }
        return count;
    }
}

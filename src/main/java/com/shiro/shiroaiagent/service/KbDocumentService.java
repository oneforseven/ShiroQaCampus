package com.shiro.shiroaiagent.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiro.shiroaiagent.constant.FileConstant;
import com.shiro.shiroaiagent.entity.KbDocument;
import com.shiro.shiroaiagent.entity.KbDocumentReview;
import com.shiro.shiroaiagent.mapper.KbDocumentMapper;
import com.shiro.shiroaiagent.mapper.KbDocumentReviewMapper;
import com.shiro.shiroaiagent.rag.KbIngestService;
import com.shiro.shiroaiagent.security.LoginUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 知识库文档管理（管理员端的"文档库"）
 *
 * 完整生命周期（与 kb_document 表状态一一对应）：
 *   上传 ──► 待审核 ──┬── 审核通过 ──► 已通过（切分 + 向量化 + 写入 pgvector）
 *                     └── 驳回    ──► 已驳回（不产生任何向量）
 *   已通过 ──► 删除：删向量 → 删文件 → 删表记录（物理删除，三处一起清）
 *
 * 设计原则：
 * <ol>
 *   <li><b>表是唯一真相</b>：文档集合 = {@code kb_document} 里 status=已通过 的行，
 *       不再由代码里的文件名列表决定。加文档不用改代码。</li>
 *   <li><b>入库只发生在"审核通过"这一步</b>：上传本身不调 Embedding 接口，
 *       所以上传很快、失败也不会污染向量库。</li>
 *   <li><b>删除顺序不能反</b>：先删向量、再删文件、最后删记录。
 *       若先删记录，删除向量失败时就再也没法定位这批片段了（会变成无主向量）。</li>
 *   <li><b>知识库变更必须清问答缓存</b>：否则学生问到的问题会命中 24 小时前的旧答案。</li>
 * </ol>
 */
@Service
@Slf4j
public class KbDocumentService {

    /** 待审核：已上传，尚未产生向量 */
    public static final String STATUS_PENDING = "待审核";
    /** 已通过：已切分入库，可被检索 */
    public static final String STATUS_APPROVED = "已通过";
    /** 已驳回：审核不通过，不产生向量 */
    public static final String STATUS_REJECTED = "已驳回";

    public static final String VECTOR_PENDING = "未入库";
    public static final String VECTOR_DONE = "已入库";

    public static final String VISIBILITY_PUBLIC = "公开";

    /** 可选场景（含"其他"兜底，替代原先硬编码在代码里的文件名映射） */
    public static final List<String> SCENES = List.of(
            "教务问答", "学生事务", "校园生活", "图书馆服务", "就业服务", "校园导航", "其他");

    /** 单文件上限：校园文档一般几十 KB，5MB 足够，也避免误传大文件拖慢入库 */
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;

    private static final String MD_SUFFIX = ".md";

    private final KbDocumentMapper kbDocumentMapper;

    private final KbDocumentReviewMapper kbDocumentReviewMapper;

    private final KbIngestService kbIngestService;

    private final QaCacheService qaCacheService;

    /** 文档落盘目录，默认 tmp/kb（可配置，便于测试指向临时目录） */
    @Value("${campus.kb.upload-dir:}")
    private String uploadDirConfig;

    public KbDocumentService(KbDocumentMapper kbDocumentMapper,
                             KbDocumentReviewMapper kbDocumentReviewMapper,
                             KbIngestService kbIngestService,
                             QaCacheService qaCacheService) {
        this.kbDocumentMapper = kbDocumentMapper;
        this.kbDocumentReviewMapper = kbDocumentReviewMapper;
        this.kbIngestService = kbIngestService;
        this.qaCacheService = qaCacheService;
    }

    // ==================== 上传 ====================

    /**
     * 上传文档（只落盘 + 登记，<b>不调 Embedding 接口</b>）
     *
     * @param file       上传的 .md 文件
     * @param title      展示标题，留空则用文件名（去扩展名）
     * @param scene      场景分类，必须是 {@link #SCENES} 中的值
     * @param visibility 可见范围，留空默认"公开"
     * @param deptId     归属部门（可空）
     * @param operator   当前管理员
     * @return 新建的文档记录（status=待审核）
     */
    public KbDocument upload(MultipartFile file, String title, String scene, String visibility,
                             Long deptId, LoginUser operator) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("文件不能超过 5MB，当前大小：" + file.getSize() + " 字节");
        }

        String originalName = safeFileName(file.getOriginalFilename());
        if (!originalName.toLowerCase(Locale.ROOT).endsWith(MD_SUFFIX)) {
            throw new IllegalArgumentException("目前只支持 Markdown（.md）文件，当前文件：" + originalName);
        }
        String sceneValue = normalizeScene(scene);
        String visibilityValue = StrUtil.blankToDefault(visibility, VISIBILITY_PUBLIC);

        Path stored;
        try (InputStream in = file.getInputStream()) {
            stored = storeFile(in, originalName);
        } catch (IOException e) {
            throw new IllegalStateException("保存上传文件失败：" + e.getMessage(), e);
        }

        KbDocument doc = new KbDocument();
        doc.setTitle(StrUtil.blankToDefault(title, stripMdSuffix(originalName)).trim());
        doc.setFileName(originalName);
        doc.setFilePath(stored.toString());
        doc.setFileType("md");
        doc.setFileSize(file.getSize());
        doc.setScene(sceneValue);
        doc.setDeptId(deptId);
        doc.setVisibility(visibilityValue);
        doc.setStatus(STATUS_PENDING);
        doc.setUploaderId(operator == null ? null : operator.getUserId());
        doc.setUploaderType("管理员");
        doc.setChunkCount(0);
        doc.setVectorStatus(VECTOR_PENDING);
        doc.setVersion(1);
        OffsetDateTime now = OffsetDateTime.now();
        doc.setCreatedAt(now);
        doc.setUpdatedAt(now);

        kbDocumentMapper.insert(doc);
        addReview(doc.getId(), doc.getUploaderId(), "提交", "管理员上传，等待审核");

        log.info("知识库文档已上传待审核：id={}, title={}, scene={}, size={}",
                doc.getId(), doc.getTitle(), sceneValue, file.getSize());
        return doc;
    }

    /**
     * 内置语料专用：登记并<b>直接入库</b>（免审核）
     *
     * <p>只被 {@link com.shiro.shiroaiagent.rag.CampusKbIngestService} 调用——
     * 内置的 7 篇语料是项目自带的可信内容，首次部署时不该卡在人工审核上。
     * 管理端新增的文档<b>一律</b>走 {@link #upload} + {@link #approve}。</p>
     */
    public KbDocument registerAndIngest(String fileName, String scene, byte[] content) {
        String name = safeFileName(fileName);
        Path stored;
        try {
            stored = storeFile(new ByteArrayInputStream(content), name);
        } catch (IOException e) {
            throw new IllegalStateException("内置语料落盘失败：" + e.getMessage(), e);
        }

        KbDocument doc = new KbDocument();
        doc.setTitle(stripMdSuffix(name));
        doc.setFileName(name);
        doc.setFilePath(stored.toString());
        doc.setFileType("md");
        doc.setFileSize((long) content.length);
        doc.setScene(normalizeScene(scene));
        doc.setVisibility(VISIBILITY_PUBLIC);
        doc.setStatus(STATUS_APPROVED);
        doc.setUploaderType("系统");
        doc.setChunkCount(0);
        doc.setVectorStatus(VECTOR_PENDING);
        doc.setVersion(1);
        OffsetDateTime now = OffsetDateTime.now();
        doc.setCreatedAt(now);
        doc.setUpdatedAt(now);
        kbDocumentMapper.insert(doc);

        try {
            int chunks = kbIngestService.ingest(doc, stored);
            doc.setChunkCount(chunks);
            doc.setVectorStatus(VECTOR_DONE);
            doc.setUpdatedAt(OffsetDateTime.now());
            kbDocumentMapper.updateById(doc);
        } catch (RuntimeException e) {
            // 入库失败就把这条半成品记录清掉，避免表里留一条"永远入不了库"的脏数据
            log.error("内置语料入库失败，回滚记录：id={}, file={}", doc.getId(), name, e);
            kbDocumentMapper.deleteById(doc.getId());
            deleteFileQuietly(doc.getFilePath());
            throw e;
        }

        addReview(doc.getId(), null, "通过", "内置语料初始化，免审核入库");
        return doc;
    }

    // ==================== 查询 ====================

    /**
     * 分页查询文档列表（管理端"文档库"主界面）
     *
     * @param current 页码，从 1 开始
     * @param size    每页条数，上限 100
     * @param status  按状态筛选（可空）
     * @param scene   按场景筛选（可空）
     * @param keyword 标题/文件名模糊搜索（可空）
     */
    public Page<KbDocument> page(long current, long size, String status, String scene, String keyword) {
        long pageNo = Math.max(1, current);
        long pageSize = Math.min(Math.max(1, size), 100);
        Page<KbDocument> page = new Page<>(pageNo, pageSize);

        LambdaQueryWrapper<KbDocument> query = new LambdaQueryWrapper<>();
        query.eq(StrUtil.isNotBlank(status), KbDocument::getStatus, status)
                .eq(StrUtil.isNotBlank(scene), KbDocument::getScene, scene)
                .and(StrUtil.isNotBlank(keyword), w -> w
                        .like(KbDocument::getTitle, keyword)
                        .or()
                        .like(KbDocument::getFileName, keyword))
                .orderByDesc(KbDocument::getCreatedAt)
                .orderByDesc(KbDocument::getId);
        return kbDocumentMapper.selectPage(page, query);
    }

    /**
     * 文档详情 + 审核轨迹
     */
    public Map<String, Object> detail(Long id) {
        KbDocument doc = requireDocument(id);
        List<KbDocumentReview> reviews = kbDocumentReviewMapper.selectList(
                new LambdaQueryWrapper<KbDocumentReview>()
                        .eq(KbDocumentReview::getDocumentId, id)
                        .orderByAsc(KbDocumentReview::getId));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("document", doc);
        result.put("reviews", reviews);
        return result;
    }

    /** 内置语料初始化时的幂等判断：同名文件已登记过就跳过 */
    public boolean existsByFileName(String fileName) {
        Long count = kbDocumentMapper.selectCount(
                new LambdaQueryWrapper<KbDocument>().eq(KbDocument::getFileName, fileName));
        return count != null && count > 0;
    }

    // ==================== 审核 ====================

    /**
     * 审核通过 → 切分 + 向量化 + 写入 pgvector
     *
     * <p>对"已驳回"的文档再次通过是允许的（先驳回后补材料的正常流程）；
     * 对"已通过"的文档重复通过则拒绝——要重灌请用 {@link #reingest}，
     * 否则会重复叠加向量。</p>
     *
     * @param remark 审核意见（可空）
     */
    public Map<String, Object> approve(Long id, String remark, LoginUser operator) {
        KbDocument doc = requireDocument(id);
        if (!STATUS_PENDING.equals(doc.getStatus()) && !STATUS_REJECTED.equals(doc.getStatus())) {
            throw new IllegalArgumentException(
                    "只有【待审核】或【已驳回】的文档可以审核通过，当前状态：" + doc.getStatus());
        }
        Path mdFile = existingFile(doc);

        // 幂等：重试/重建时先清掉这篇文档的旧片段，避免重复叠加
        kbIngestService.deleteByDocIdQuietly(id);
        int chunks = kbIngestService.ingest(doc, mdFile);

        OffsetDateTime now = OffsetDateTime.now();
        doc.setStatus(STATUS_APPROVED);
        doc.setVectorStatus(VECTOR_DONE);
        doc.setChunkCount(chunks);
        doc.setReviewerId(operator == null ? null : operator.getUserId());
        doc.setReviewedAt(now);
        doc.setReviewRemark(StrUtil.blankToDefault(remark, "审核通过"));
        doc.setVersion(doc.getVersion() == null ? 2 : doc.getVersion() + 1);
        doc.setUpdatedAt(now);
        kbDocumentMapper.updateById(doc);

        addReview(id, doc.getReviewerId(), "通过", doc.getReviewRemark());
        // 知识库内容变了：清掉知识类缓存，否则 24 小时内的旧答案会盖住新语料
        long evicted = qaCacheService.evictAllKnowledge();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("documentId", id);
        result.put("chunkCount", chunks);
        result.put("evictedCache", evicted);
        result.put("message", "审核通过，已切分 " + chunks + " 个片段写入向量库");
        log.info("文档审核通过并入库：id={}, title={}, 片段数={}", id, doc.getTitle(), chunks);
        return result;
    }

    /**
     * 驳回（不产生向量，文档保留在库里可重新审核）
     */
    public Map<String, Object> reject(Long id, String remark, LoginUser operator) {
        KbDocument doc = requireDocument(id);
        if (!STATUS_PENDING.equals(doc.getStatus())) {
            throw new IllegalArgumentException("只有【待审核】的文档可以驳回，当前状态：" + doc.getStatus());
        }
        String remarkValue = StrUtil.blankToDefault(remark, "").trim();
        if (StrUtil.isBlank(remarkValue)) {
            throw new IllegalArgumentException("驳回必须填写原因");
        }

        OffsetDateTime now = OffsetDateTime.now();
        doc.setStatus(STATUS_REJECTED);
        doc.setReviewerId(operator == null ? null : operator.getUserId());
        doc.setReviewedAt(now);
        doc.setReviewRemark(remarkValue);
        doc.setUpdatedAt(now);
        kbDocumentMapper.updateById(doc);

        addReview(id, doc.getReviewerId(), "驳回", remarkValue);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("documentId", id);
        result.put("message", "已驳回：" + remarkValue);
        log.info("文档已驳回：id={}, 原因={}", id, remarkValue);
        return result;
    }

    /**
     * 重建向量（文档内容或切分策略变化后使用）
     *
     * <p>仅对"已通过"的文档可用：先删掉这篇文档的全部片段，再按当前切分策略重灌。
     * 换切分器做 E2 实验时，这个接口就是"按篇重灌"的入口。</p>
     */
    public Map<String, Object> reingest(Long id) {
        KbDocument doc = requireDocument(id);
        if (!STATUS_APPROVED.equals(doc.getStatus())) {
            throw new IllegalArgumentException("只有【已通过】的文档可以重建向量，当前状态：" + doc.getStatus());
        }
        Path mdFile = existingFile(doc);

        kbIngestService.deleteByDocIdQuietly(id);
        int chunks = kbIngestService.ingest(doc, mdFile);

        doc.setChunkCount(chunks);
        doc.setVectorStatus(VECTOR_DONE);
        doc.setVersion(doc.getVersion() == null ? 2 : doc.getVersion() + 1);
        doc.setUpdatedAt(OffsetDateTime.now());
        kbDocumentMapper.updateById(doc);

        long evicted = qaCacheService.evictAllKnowledge();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("documentId", id);
        result.put("chunkCount", chunks);
        result.put("evictedCache", evicted);
        result.put("message", "已重建向量：" + chunks + " 个片段");
        log.info("文档向量已重建：id={}, 片段数={}", id, chunks);
        return result;
    }

    // ==================== 删除（物理） ====================

    /**
     * 物理删除：向量 → 磁盘文件 → 表记录（审核记录由外键级联删除）
     *
     * <p><b>顺序不能反</b>：先删表记录就再也定位不到这批向量了。
     * 若删向量失败，这里直接抛异常中止，保证"表里说删了"与"向量库里真的没有"一致。</p>
     */
    public Map<String, Object> delete(Long id) {
        KbDocument doc = requireDocument(id);

        boolean hadVectors = VECTOR_DONE.equals(doc.getVectorStatus());
        if (hadVectors) {
            kbIngestService.deleteByDocId(id);
        }
        // 占位/历史数据可能本来就没有磁盘文件，删不到不算错
        boolean fileDeleted = deleteFileQuietly(doc.getFilePath());

        kbDocumentMapper.deleteById(id);

        long evicted = hadVectors ? qaCacheService.evictAllKnowledge() : 0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("documentId", id);
        result.put("vectorsDeleted", hadVectors);
        result.put("fileDeleted", fileDeleted);
        result.put("evictedCache", evicted);
        result.put("message", "文档已删除（向量/文件/记录）");
        log.info("知识库文档已删除：id={}, title={}, 含向量={}, 含文件={}",
                id, doc.getTitle(), hadVectors, fileDeleted);
        return result;
    }

    // ==================== 内部工具 ====================

    private KbDocument requireDocument(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("文档 id 不能为空");
        }
        KbDocument doc = kbDocumentMapper.selectById(id);
        if (doc == null) {
            throw new IllegalArgumentException("文档不存在：id=" + id);
        }
        return doc;
    }

    /** 取文档对应的磁盘文件，不存在时给出可操作的提示 */
    private Path existingFile(KbDocument doc) {
        if (StrUtil.isBlank(doc.getFilePath())) {
            throw new IllegalArgumentException("该文档没有可入库的文件（id=" + doc.getId() + "），请删除后重新上传");
        }
        Path path = Paths.get(doc.getFilePath());
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException(
                    "文档文件已丢失（" + doc.getFilePath() + "），请删除后重新上传再入库");
        }
        return path;
    }

    private void addReview(Long documentId, Long reviewerId, String action, String remark) {
        KbDocumentReview review = new KbDocumentReview();
        review.setDocumentId(documentId);
        review.setReviewerId(reviewerId);
        review.setAction(action);
        review.setRemark(remark);
        review.setCreatedAt(OffsetDateTime.now());
        kbDocumentReviewMapper.insert(review);
    }

    private Path uploadDir() {
        return StrUtil.isBlank(uploadDirConfig) ? Paths.get(FileConstant.KB_DIR) : Paths.get(uploadDirConfig);
    }

    /**
     * 落盘：文件名加时间戳 + 随机前缀，避免同名覆盖
     * <p>同时做一次路径校验，杜绝 {@code ../../} 之类的目录穿越。</p>
     */
    private Path storeFile(InputStream in, String originalName) throws IOException {
        Path dir = uploadDir().toAbsolutePath().normalize();
        Files.createDirectories(dir);
        String storedName = System.currentTimeMillis() + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + "-" + originalName;
        Path target = dir.resolve(storedName).normalize();
        if (!target.startsWith(dir)) {
            throw new IllegalArgumentException("文件名不合法：" + originalName);
        }
        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    private boolean deleteFileQuietly(String filePath) {
        if (StrUtil.isBlank(filePath)) {
            return false;
        }
        try {
            return Files.deleteIfExists(Paths.get(filePath));
        } catch (Exception e) {
            log.warn("删除文档文件失败（不影响记录删除）：{}，原因={}", filePath, e.getMessage());
            return false;
        }
    }

    /**
     * 清洗上传文件名：去掉浏览器可能带上的路径（IE 会传 {@code C:\fakepath\x.md}），
     * 替换 Windows 非法字符，防止目录穿越。
     */
    private String safeFileName(String raw) {
        String name = StrUtil.blankToDefault(raw, "").trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (StrUtil.isBlank(name)) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        return name;
    }

    private String stripMdSuffix(String fileName) {
        if (fileName == null || fileName.length() <= MD_SUFFIX.length()) {
            return StrUtil.blankToDefault(fileName, "未命名文档");
        }
        return fileName.substring(0, fileName.length() - MD_SUFFIX.length());
    }

    private String normalizeScene(String scene) {
        String value = StrUtil.blankToDefault(scene, "").trim();
        if (StrUtil.isBlank(value)) {
            throw new IllegalArgumentException("请选择文档场景，可选：" + String.join("、", SCENES));
        }
        if (!SCENES.contains(value)) {
            throw new IllegalArgumentException("未知场景：" + value + "，可选：" + String.join("、", SCENES));
        }
        return value;
    }
}

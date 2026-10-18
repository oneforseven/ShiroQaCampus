package com.shiro.shiroaiagent.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiro.shiroaiagent.entity.KbDocument;
import com.shiro.shiroaiagent.rag.CampusKbIngestService;
import com.shiro.shiroaiagent.security.CurrentUserHolder;
import com.shiro.shiroaiagent.security.LoginUser;
import com.shiro.shiroaiagent.service.KbDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 管理端 · 知识库（文档库管理）
 *
 * <p><b>完整流程：</b>上传文档 → 待审核 → 审核通过（自动切分 + 向量化入库）/ 驳回；
 * 已通过的文档可删除（向量、文件、记录一并清除）或重建向量。</p>
 *
 * <p><b>为什么新增文档不再需要改代码：</b>文档集合由 {@code kb_document} 表决定，
 * 场景由管理员上传时选择。旧实现"扫描 jar 内固定目录 + 代码里硬编码文件名映射"的方式
 * 已退化为首次部署时的内置语料引导（{@link #ingest()}）。</p>
 *
 * <p><b>权限：</b>当前全部接口要求 {@code super_admin}。</p>
 */
@Tag(name = "管理端 · 知识库", description = "文档库管理：上传、审核、入库、删除（需要超级管理员）")
@RestController
@RequestMapping("/admin/kb")
@Slf4j
public class AdminKbController {

    @Resource
    private KbDocumentService kbDocumentService;

    @Resource
    private CampusKbIngestService campusKbIngestService;

    // ==================== 文档上传与查询 ====================

    @Operation(summary = "上传文档（只登记，待审核）",
            description = "只支持 .md，最大 5MB。上传后状态为【待审核】，不会调用 Embedding 接口；"
                    + "审核通过后才会切分入库。title 留空则用文件名（去扩展名）。")
    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public KbDocument upload(
            @Parameter(description = "Markdown 文件") @RequestPart("file") MultipartFile file,
            @Parameter(description = "展示标题，留空用文件名") @RequestParam(required = false) String title,
            @Parameter(description = "场景：教务问答/学生事务/校园生活/图书馆服务/就业服务/校园导航/其他")
            @RequestParam String scene,
            @Parameter(description = "可见范围，默认公开") @RequestParam(required = false) String visibility,
            @Parameter(description = "归属部门 id，可空") @RequestParam(required = false) Long deptId) {
        requireSuperAdmin();
        LoginUser operator = CurrentUserHolder.currentLoginUser();
        return kbDocumentService.upload(file, title, scene, visibility, deptId, operator);
    }

    @Operation(summary = "文档列表（分页）",
            description = "支持按状态、场景筛选与标题/文件名模糊搜索。管理端文档库主界面用。")
    @GetMapping("/documents")
    public Page<KbDocument> list(
            @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") long current,
            @Parameter(description = "每页条数，上限 100") @RequestParam(defaultValue = "10") long size,
            @Parameter(description = "状态：待审核/已通过/已驳回") @RequestParam(required = false) String status,
            @Parameter(description = "场景") @RequestParam(required = false) String scene,
            @Parameter(description = "标题或文件名关键词") @RequestParam(required = false) String keyword) {
        requireSuperAdmin();
        return kbDocumentService.page(current, size, status, scene, keyword);
    }

    @Operation(summary = "文档详情 + 审核轨迹", description = "返回 document 与 reviews（提交/通过/驳回记录）")
    @GetMapping("/documents/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        requireSuperAdmin();
        return kbDocumentService.detail(id);
    }

    @Operation(summary = "可选场景列表", description = "供管理端页面渲染场景下拉框")
    @GetMapping("/scenes")
    public List<String> scenes() {
        requireSuperAdmin();
        return KbDocumentService.SCENES;
    }

    // ==================== 审核 ====================

    @Operation(summary = "审核通过 → 切分并入库",
            description = "按当前切分策略切分文档、调用 Embedding 接口写入 pgvector，"
                    + "并把片段数回填到 chunk_count。入库成功后自动清除知识类问答缓存。"
                    + "可对【已驳回】的文档重新通过；【已通过】的文档请用重建接口。")
    @PostMapping("/documents/{id}/approve")
    public Map<String, Object> approve(@PathVariable Long id,
                                       @Parameter(description = "审核意见") @RequestParam(required = false) String remark) {
        requireSuperAdmin();
        return kbDocumentService.approve(id, remark, CurrentUserHolder.currentLoginUser());
    }

    @Operation(summary = "驳回（不产生向量）", description = "必须填写原因，文档保留在库中，可补材料后重新审核")
    @PostMapping("/documents/{id}/reject")
    public Map<String, Object> reject(@PathVariable Long id,
                                      @Parameter(description = "驳回原因，必填") @RequestParam String remark) {
        requireSuperAdmin();
        return kbDocumentService.reject(id, remark, CurrentUserHolder.currentLoginUser());
    }

    @Operation(summary = "重建向量", description = "删掉这篇文档的旧片段后按当前切分策略重灌；换切分器做 E2 实验时用")
    @PostMapping("/documents/{id}/reingest")
    public Map<String, Object> reingest(@PathVariable Long id) {
        requireSuperAdmin();
        return kbDocumentService.reingest(id);
    }

    // ==================== 删除 ====================

    @Operation(summary = "删除文档（物理删除）",
            description = "先删该文档的全部向量，再删磁盘文件，最后删表记录（审核记录随外键级联删除）。"
                    + "删除后自动清除知识类问答缓存。")
    @DeleteMapping("/documents/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        requireSuperAdmin();
        return kbDocumentService.delete(id);
    }

    // ==================== 内置语料引导 ====================

    @Operation(summary = "初始化内置语料（幂等）",
            description = "把 classpath:campus/*.md 的 7 篇内置语料登记为已通过文档并入库。"
                    + "重复调用会跳过已存在的同名文档；同时清理旧流程遗留的、没有 docId 的无主向量。"
                    + "新环境首次部署调用一次即可，日常增删文档请用上传/删除接口。")
    @PostMapping("/ingest")
    public Map<String, Object> ingest() {
        requireSuperAdmin();
        return campusKbIngestService.seedBuiltIn();
    }

    /** 当前管理端知识库接口统一要求超级管理员 */
    private void requireSuperAdmin() {
        StpUtil.checkRole("super_admin");
    }
}
